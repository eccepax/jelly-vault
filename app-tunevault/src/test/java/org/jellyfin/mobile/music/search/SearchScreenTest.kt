package org.jellyfin.mobile.music.search

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.catalog.SearchResults
import org.jellyfin.mobile.music.catalog.album
import org.jellyfin.mobile.music.domain.Artist
import org.jellyfin.mobile.music.domain.track
import org.jellyfin.mobile.music.library.TrackListActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class SearchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val results = SearchResults(
        artists = listOf(Artist("k", "Kendrick Lamar")),
        albums = listOf(album("damn", "Damn", artist = "Kendrick Lamar")),
        tracks = listOf(track("h").copy(title = "Humble", artist = "Kendrick Lamar", albumTitle = "Damn")),
    )
    private val events = mutableListOf<String>()

    private fun show(state: SearchUiState, initialQuery: String = "") {
        composeRule.setContent {
            var query by mutableStateOf(initialQuery)
            SearchScreen(
                query = query,
                state = state,
                onQueryChange = {
                    query = it
                    events += "query '$it'"
                },
                onRetry = { events += "retry" },
                onArtistClick = { events += "artist ${it.id}" },
                onAlbumClick = { events += "album ${it.id}" },
                onTrackClick = { events += "track $it" },
                trackActions = TrackListActions(
                    onPlayNext = { events += "playNext ${it.id}" },
                    onAddToQueue = { events += "addToQueue ${it.id}" },
                ),
            )
        }
    }

    @Test
    fun `typing updates the query`() {
        show(SearchUiState.Idle)

        composeRule.onNodeWithText("Search your music").performTextInput("damn")

        events.last() shouldBe "query 'damn'"
    }

    @Test
    fun `idle invites a search`() {
        show(SearchUiState.Idle)

        composeRule.onNodeWithText("Find artists, albums and songs").assertIsDisplayed()
    }

    @Test
    fun `the clear button empties the query`() {
        show(SearchUiState.Results(results), initialQuery = "damn")

        composeRule.onNodeWithContentDescription("Clear search").performClick()

        events.last() shouldBe "query ''"
    }

    @Test
    fun `results are grouped into artists, albums and songs`() {
        show(SearchUiState.Results(results), initialQuery = "kendrick")

        composeRule.onNodeWithText("Artists").assertIsDisplayed()
        composeRule.onNodeWithText("Albums").assertIsDisplayed()
        composeRule.onNodeWithText("Songs").assertIsDisplayed()
        composeRule.onNodeWithText("Humble").assertIsDisplayed()
    }

    @Test
    fun `empty sections are left out`() {
        show(SearchUiState.Results(results.copy(artists = emptyList(), albums = emptyList())), initialQuery = "humble")

        composeRule.onNodeWithText("Artists").assertDoesNotExist()
        composeRule.onNodeWithText("Albums").assertDoesNotExist()
        composeRule.onNodeWithText("Songs").assertIsDisplayed()
    }

    @Test
    fun `tapping a result opens or plays it`() {
        show(SearchUiState.Results(results), initialQuery = "kendrick")

        composeRule.onNodeWithText("Kendrick Lamar").performClick()
        composeRule.onNodeWithText("Damn").performClick()
        composeRule.onNodeWithText("Humble").performClick()

        events shouldBe listOf("artist k", "album damn", "track 0")
    }

    @Test
    fun `long-pressing a found song offers queue actions`() {
        show(SearchUiState.Results(results), initialQuery = "kendrick")

        composeRule.onNodeWithText("Humble").performTouchInput { longClick() }
        composeRule.onNodeWithText("Play next").performClick()

        events.last() shouldBe "playNext h"
    }

    @Test
    fun `searching shows progress`() {
        show(SearchUiState.Searching, initialQuery = "damn")

        composeRule.onNodeWithTag(SearchScreenTags.SEARCHING).assertIsDisplayed()
    }

    @Test
    fun `no results names the query`() {
        show(SearchUiState.NoResults("zzz"), initialQuery = "zzz")

        composeRule.onNodeWithText("No results for “zzz”").assertIsDisplayed()
    }

    @Test
    fun `an error offers a retry`() {
        show(SearchUiState.Error, initialQuery = "damn")

        composeRule.onNodeWithText("Search failed").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        events.last() shouldBe "retry"
    }
}
