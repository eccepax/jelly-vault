package org.jellyfin.mobile.music.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import org.jellyfin.mobile.music.catalog.MusicSearch
import org.jellyfin.mobile.music.catalog.SearchResults
import org.jellyfin.mobile.music.playback.PlaybackController
import timber.log.Timber

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Searching : SearchUiState
    data class Results(val results: SearchResults) : SearchUiState
    data class NoResults(val query: String) : SearchUiState
    data object Error : SearchUiState
}

/**
 * Searches as you type: waits for a [DEBOUNCE_MS] pause, abandons a search the moment the text
 * changes again, and clears instantly. The query is kept in [SavedStateHandle] so it survives process
 * death as well as rotation.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val search: MusicSearch,
    private val playback: PlaybackController,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    val query: StateFlow<String> = savedState.getStateFlow(KEY_QUERY, "")
    private val retries = MutableStateFlow(0)

    val state: StateFlow<SearchUiState> = combine(
        query.debounce { if (it.isBlank()) 0L else DEBOUNCE_MS },
        retries,
    ) { text, _ -> text.trim() }
        .flatMapLatest { text -> if (text.isEmpty()) flowOf(SearchUiState.Idle) else searchFor(text) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SearchUiState.Idle)

    fun onQueryChange(text: String) {
        savedState[KEY_QUERY] = text
    }

    fun retry() {
        retries.value++
    }

    /** Plays the matching songs, starting from the one tapped. */
    fun playTrack(index: Int) {
        val tracks = (state.value as? SearchUiState.Results)?.results?.tracks.orEmpty()
        if (index in tracks.indices) playback.play(tracks, startIndex = index)
    }

    private fun searchFor(text: String) = flow {
        emit(SearchUiState.Searching)
        emit(
            try {
                val results = search.search(text)
                if (results.isEmpty) SearchUiState.NoResults(text) else SearchUiState.Results(results)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Search failed")
                SearchUiState.Error
            },
        )
    }

    companion object {
        const val DEBOUNCE_MS = 300L
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val KEY_QUERY = "query"
    }
}
