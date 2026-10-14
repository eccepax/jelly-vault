package org.jellyfin.mobile.livetv.channels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.mobile.livetv.catalog.ChannelFilter
import org.jellyfin.mobile.livetv.catalog.LiveTvCatalog
import org.jellyfin.mobile.livetv.catalog.filteredBy
import org.jellyfin.mobile.livetv.catalog.tags
import org.jellyfin.mobile.livetv.domain.LiveChannel
import timber.log.Timber
import java.time.Instant

sealed interface ChannelsUiState {
    data object Loading : ChannelsUiState
    data object Error : ChannelsUiState

    /** [channels] are the ones [filter] lets through; [tags] are every tag in use. */
    data class Loaded(
        val channels: List<LiveChannel>,
        val tags: List<String>,
        val filter: ChannelFilter,
    ) : ChannelsUiState
}

/**
 * The channel browser: loads every channel once and filters on the device, so switching tags or
 * typing a search is instant. The filter is kept in [SavedStateHandle] so it survives rotation and
 * process death.
 */
class ChannelsViewModel(
    private val catalog: LiveTvCatalog,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private sealed interface Channels {
        data object Loading : Channels
        data object Error : Channels
        data class Loaded(val all: List<LiveChannel>) : Channels
    }

    private val channels = MutableStateFlow<Channels>(Channels.Loading)

    val state: StateFlow<ChannelsUiState> = combine(
        channels,
        savedState.getStateFlow<String?>(KEY_TAG, null),
        savedState.getStateFlow(KEY_FAVORITES, false),
        savedState.getStateFlow(KEY_QUERY, ""),
    ) { channels, tag, favoritesOnly, query ->
        when (channels) {
            Channels.Loading -> ChannelsUiState.Loading
            Channels.Error -> ChannelsUiState.Error
            is Channels.Loaded -> {
                val filter = ChannelFilter(tag = tag, favoritesOnly = favoritesOnly, query = query)
                ChannelsUiState.Loaded(channels.all.filteredBy(filter), channels.all.tags(), filter)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChannelsUiState.Loading)

    init {
        retry()
    }

    fun retry() {
        channels.value = Channels.Loading
        load(showingErrors = true)
    }

    /**
     * Reloads what's on now, keeping the current channels on screen (and on a failure). After a
     * failed first load it retries; during the first load it does nothing.
     */
    fun refresh() {
        when (channels.value) {
            is Channels.Loaded -> load(showingErrors = false)
            Channels.Error -> retry()
            Channels.Loading -> Unit
        }
    }

    /** Refreshes once any channel's current program has ended by [now]. */
    fun refreshIfEnded(now: Instant) {
        val all = (channels.value as? Channels.Loaded)?.all ?: return
        if (all.any { channel -> channel.nowPlaying?.let { !it.end.isAfter(now) } == true }) refresh()
    }

    /** Shows the channels carrying [tag], or every channel when null or already selected. */
    fun selectTag(tag: String?) {
        val selected = savedState.get<String?>(KEY_TAG)
        savedState[KEY_FAVORITES] = false
        savedState[KEY_TAG] = tag.takeIf { it != selected }
    }

    /** Shows the channels carrying [tag], whatever was selected before. */
    fun showTag(tag: String) {
        savedState[KEY_FAVORITES] = false
        savedState[KEY_TAG] = tag
    }

    /** Shows only favorites, or every channel when they're already shown. */
    fun showFavorites() {
        savedState[KEY_TAG] = null
        savedState[KEY_FAVORITES] = savedState.get<Boolean>(KEY_FAVORITES) != true
    }

    fun onQueryChange(text: String) {
        savedState[KEY_QUERY] = text
    }

    /** Flips the channel's favorite flag at once, and puts it back if the server rejects it. */
    fun toggleFavorite(channelId: String) {
        val channel = (channels.value as? Channels.Loaded)?.all?.firstOrNull { it.id == channelId } ?: return
        val favorite = !channel.isFavorite
        setFavoriteShown(channelId, favorite)
        viewModelScope.launch {
            try {
                catalog.setFavorite(channelId, favorite)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Failed to save favorite")
                setFavoriteShown(channelId, !favorite)
            }
        }
    }

    private fun setFavoriteShown(channelId: String, favorite: Boolean) {
        channels.update { state ->
            if (state !is Channels.Loaded) return@update state
            Channels.Loaded(state.all.map { if (it.id == channelId) it.copy(isFavorite = favorite) else it })
        }
    }

    private fun load(showingErrors: Boolean) {
        viewModelScope.launch {
            try {
                channels.value = Channels.Loaded(catalog.channels())
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Failed to load channels")
                if (showingErrors) channels.value = Channels.Error
            }
        }
    }

    private companion object {
        const val KEY_TAG = "tag"
        const val KEY_FAVORITES = "favorites"
        const val KEY_QUERY = "query"
    }
}
