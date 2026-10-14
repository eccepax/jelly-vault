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
import org.jellyfin.mobile.livetv.viewing.ViewingMemory
import timber.log.Timber
import java.time.Instant

sealed interface ChannelsUiState {
    data object Loading : ChannelsUiState
    data object Error : ChannelsUiState

    /**
     * [channels] are the ones [filter] lets through; [tags] are every tag in use. [lastChannel] is
     * the channel last watched, offered to continue (not while searching).
     */
    data class Loaded(
        val channels: List<LiveChannel>,
        val tags: List<String>,
        val filter: ChannelFilter,
        val lastChannel: LiveChannel? = null,
    ) : ChannelsUiState
}

/**
 * The channel browser: loads every channel once and filters on the device, so switching tags or
 * typing a search is instant. The filter is kept in [SavedStateHandle] so it survives rotation and
 * process death, and the tag (or Favorites) in [memory] for the next time the app opens.
 */
class ChannelsViewModel(
    private val catalog: LiveTvCatalog,
    private val savedState: SavedStateHandle,
    private val memory: ViewingMemory,
) : ViewModel() {

    init {
        // A new screen opens where the viewer left off; a recreated one keeps its own filter.
        if (KEY_TAG !in savedState && KEY_FAVORITES !in savedState) {
            savedState[KEY_TAG] = memory.tag
            savedState[KEY_FAVORITES] = memory.favoritesOnly
        }
    }

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
        memory.lastChannelId,
    ) { channels, tag, favoritesOnly, query, lastChannelId ->
        when (channels) {
            Channels.Loading -> ChannelsUiState.Loading
            Channels.Error -> ChannelsUiState.Error
            is Channels.Loaded -> {
                val filter = ChannelFilter(tag = tag, favoritesOnly = favoritesOnly, query = query)
                ChannelsUiState.Loaded(
                    channels = channels.all.filteredBy(filter),
                    tags = channels.all.tags(),
                    filter = filter,
                    lastChannel = channels.all.firstOrNull { it.id == lastChannelId }.takeIf { query.isBlank() },
                )
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
        setFilter(tag = tag.takeIf { it != selected }, favoritesOnly = false)
    }

    /** Shows the channels carrying [tag], whatever was selected before. */
    fun showTag(tag: String) {
        setFilter(tag = tag, favoritesOnly = false)
    }

    /** Shows only favorites, or every channel when they're already shown. */
    fun showFavorites() {
        setFilter(tag = null, favoritesOnly = savedState.get<Boolean>(KEY_FAVORITES) != true)
    }

    private fun setFilter(tag: String?, favoritesOnly: Boolean) {
        savedState[KEY_TAG] = tag
        savedState[KEY_FAVORITES] = favoritesOnly
        memory.rememberFilter(tag, favoritesOnly)
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
