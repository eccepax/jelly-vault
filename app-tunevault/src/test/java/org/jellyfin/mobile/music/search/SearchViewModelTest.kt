package org.jellyfin.mobile.music.search

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.music.catalog.FakeMusicSearch
import org.jellyfin.mobile.music.catalog.SearchResults
import org.jellyfin.mobile.music.catalog.album
import org.jellyfin.mobile.music.domain.track
import org.jellyfin.mobile.music.playback.FakePlaybackController
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val purple = SearchResults(
        artists = emptyList(),
        albums = listOf(album("pb", "The Purple Bird")),
        tracks = emptyList(),
    )
    private val london = SearchResults(
        artists = emptyList(),
        albums = emptyList(),
        tracks = listOf(track("lm").copy(title = "London May"), track("lm2").copy(title = "London Calling")),
    )
    private val search = FakeMusicSearch(mapOf("purple" to purple, "london" to london))
    private val playback = FakePlaybackController()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(saved: SavedStateHandle = SavedStateHandle()) = SearchViewModel(search, playback, saved)

    @Test
    fun `starts idle with an empty query`() = runTest {
        val viewModel = viewModel()
        viewModel.state.test {
            runCurrent()
            expectMostRecentItem() shouldBe SearchUiState.Idle
            viewModel.query.value shouldBe ""
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `searches once typing pauses`() = runTest {
        val viewModel = viewModel()
        viewModel.state.test {
            runCurrent()
            viewModel.onQueryChange("purple")
            advanceTimeBy(SearchViewModel.DEBOUNCE_MS - 1)
            runCurrent()
            search.queries shouldBe emptyList()

            advanceTimeBy(2)
            runCurrent()

            expectMostRecentItem() shouldBe SearchUiState.Results(purple)
            search.queries shouldBe listOf("purple")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fast typing only searches for the final text`() = runTest {
        val viewModel = viewModel()
        viewModel.state.test {
            runCurrent()
            listOf("p", "pu", "pur", "purp", "purple").forEach {
                viewModel.onQueryChange(it)
                advanceTimeBy(50)
            }
            advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
            runCurrent()

            search.queries shouldBe listOf("purple")
            expectMostRecentItem() shouldBe SearchUiState.Results(purple)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows searching while waiting for the server`() = runTest {
        search.gate = CompletableDeferred()
        val viewModel = viewModel()
        viewModel.state.test {
            runCurrent()
            viewModel.onQueryChange("purple")
            advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
            runCurrent()

            expectMostRecentItem() shouldBe SearchUiState.Searching
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no matches says so`() = runTest {
        val viewModel = viewModel()
        viewModel.state.test {
            runCurrent()
            viewModel.onQueryChange("zzz")
            advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
            runCurrent()

            expectMostRecentItem() shouldBe SearchUiState.NoResults("zzz")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clearing the query goes back to idle at once`() = runTest {
        val viewModel = viewModel()
        viewModel.state.test {
            runCurrent()
            viewModel.onQueryChange("purple")
            advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
            runCurrent()

            viewModel.onQueryChange("")
            runCurrent()

            expectMostRecentItem() shouldBe SearchUiState.Idle
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed search offers a retry that searches again`() = runTest {
        search.failure = IOException("offline")
        val viewModel = viewModel()
        viewModel.state.test {
            runCurrent()
            viewModel.onQueryChange("purple")
            advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
            runCurrent()
            expectMostRecentItem() shouldBe SearchUiState.Error

            search.failure = null
            viewModel.retry()
            runCurrent()

            expectMostRecentItem() shouldBe SearchUiState.Results(purple)
            search.queries shouldBe listOf("purple", "purple")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tapping a song plays the matching songs from it`() = runTest {
        val viewModel = viewModel()
        viewModel.state.test {
            runCurrent()
            viewModel.onQueryChange("london")
            advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
            runCurrent()

            viewModel.playTrack(1)

            playback.commands shouldBe listOf("play lm,lm2 from 1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the query survives process death`() = runTest {
        val saved = SavedStateHandle()
        viewModel(saved).onQueryChange("purple")

        val restored = viewModel(saved)
        restored.state.test {
            runCurrent()
            advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
            runCurrent()

            restored.query.value shouldBe "purple"
            expectMostRecentItem() shouldBe SearchUiState.Results(purple)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
