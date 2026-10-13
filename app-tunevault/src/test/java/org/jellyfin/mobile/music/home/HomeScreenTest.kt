package org.jellyfin.mobile.music.home

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.catalog.album
import org.jellyfin.mobile.music.domain.track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class HomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val loaded = HomeUiState.Loaded(
        recentlyPlayed = listOf(album("damn", "Damn", artist = "Kendrick Lamar")),
        recentlyAdded = listOf(album("hw", "Homework", artist = "Daft Punk")),
        favoriteAlbums = listOf(album("pb", "The Purple Bird")),
        favoriteTracks = listOf(track("lm").copy(title = "London May"), track("om").copy(title = "Our Home")),
    )
    private val events = mutableListOf<String>()

    /** Scrolls the page (the outer list; shelves scroll sideways) until [text] is composed. */
    private fun scrollTo(text: String) {
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
    }

    private fun show(state: HomeUiState, isOffline: Boolean = false) {
        composeRule.setContent {
            HomeScreen(
                state = state,
                isOffline = isOffline,
                onRetry = { events += "retry" },
                onAlbumClick = { events += "album ${it.id}" },
                onShuffleAll = { events += "shuffle" },
                onFavoriteSongClick = { events += "favorite song $it" },
                onSettingsClick = { events += "settings" },
            )
        }
    }

    @Test
    fun `shows the home shelves`() {
        show(loaded)

        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("Recently played").assertIsDisplayed()
        composeRule.onNodeWithText("Damn").assertIsDisplayed()
        scrollTo("Recently added")
        composeRule.onNodeWithText("Recently added").assertIsDisplayed()
        composeRule.onNodeWithText("Homework").assertIsDisplayed()
    }

    @Test
    fun `tapping an album opens it`() {
        show(loaded)
        scrollTo("Recently added")

        composeRule.onNodeWithText("Homework").performClick()

        events shouldBe listOf("album hw")
    }

    @Test
    fun `shuffle all`() {
        show(loaded)

        composeRule.onNodeWithText("Shuffle all").performClick()

        events shouldBe listOf("shuffle")
    }

    @Test
    fun `shows favorite albums and songs`() {
        show(loaded)

        scrollTo("Favorite albums")
        composeRule.onNodeWithText("Favorite albums").assertIsDisplayed()
        scrollTo("Favorite songs")
        composeRule.onNodeWithText("Favorite songs").assertIsDisplayed()
    }

    @Test
    fun `tapping a favorite song plays from it`() {
        show(loaded)

        scrollTo("Favorite songs")
        composeRule.onNodeWithText("Our Home").performClick()

        events shouldBe listOf("favorite song 1")
    }

    @Test
    fun `no favorites means no favorite shelves`() {
        show(loaded.copy(favoriteAlbums = emptyList(), favoriteTracks = emptyList()))

        composeRule.onNodeWithText("Favorite albums").assertDoesNotExist()
        composeRule.onNodeWithText("Favorite songs").assertDoesNotExist()
    }

    @Test
    fun `nothing played yet explains the shelf`() {
        show(loaded.copy(recentlyPlayed = emptyList()))

        composeRule.onNodeWithText("Albums you play will show up here").assertIsDisplayed()
    }

    @Test
    fun `loading shows progress`() {
        show(HomeUiState.Loading)

        composeRule.onNodeWithTag(HomeScreenTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun `an error offers a retry`() {
        show(HomeUiState.Error)

        composeRule.onNodeWithText("Couldn't load your music").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        events shouldBe listOf("retry")
    }

    @Test
    fun `the gear opens settings`() {
        show(HomeUiState.Loading)

        composeRule.onNodeWithContentDescription("Settings").performClick()

        events shouldBe listOf("settings")
    }

    @Test
    fun `offline, home is the downloads, not shelves that need the server`() {
        show(
            HomeUiState.Loaded(recentlyPlayed = emptyList(), recentlyAdded = listOf(album("pb", "The Purple Bird"))),
            isOffline = true,
        )

        composeRule.onNodeWithText("Your downloads").assertIsDisplayed()
        composeRule.onNodeWithText("The Purple Bird").assertIsDisplayed()
        composeRule.onNodeWithText("Recently played").assertDoesNotExist()
        composeRule.onNodeWithText("Recently added").assertDoesNotExist()
        composeRule.onNodeWithText("Albums you play will show up here").assertDoesNotExist()
    }

    @Test
    fun `offline with nothing downloaded says what would help`() {
        show(HomeUiState.Loaded(recentlyPlayed = emptyList(), recentlyAdded = emptyList()), isOffline = true)

        composeRule.onNodeWithText("You're offline, and nothing is downloaded yet.").assertIsDisplayed()
    }
}
