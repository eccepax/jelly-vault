package org.jellyfin.mobile.music.library

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.catalog.album
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.AlbumSort
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Plain Application: the real TuneVaultApplication would start Koin, which this stateless screen
// doesn't need.
@RunWith(RobolectricTestRunner::class)
// Phone-sized screen (the A52ish emulator's), so the grid gets its real two columns.
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class AlbumsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(
        state: AlbumsUiState,
        onRetry: () -> Unit = {},
        onAlbumClick: (Album) -> Unit = {},
        onSortChange: (AlbumSort) -> Unit = {},
    ) {
        composeRule.setContent {
            AlbumsScreen(state = state, onRetry = onRetry, onAlbumClick = onAlbumClick, onSortChange = onSortChange)
        }
    }

    @Test
    fun `shows a progress indicator while loading`() {
        show(AlbumsUiState.Loading)

        composeRule.onNodeWithTag(AlbumsScreenTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun `shows each album's title and artist`() {
        show(AlbumsUiState.Loaded(listOf(album("1", "The Purple Bird", "Bonnie"), album("2", "Wolf of the Cosmos"))))

        composeRule.onNodeWithText("The Purple Bird").assertIsDisplayed()
        composeRule.onNodeWithText("Bonnie").assertIsDisplayed()
        composeRule.onNodeWithText("Wolf of the Cosmos").assertIsDisplayed()
    }

    @Test
    fun `the grid lays out more than its first album`() {
        // A lazy grid composes its first item even at zero size, so only a non-first album proves
        // the grid actually has room on screen.
        show(AlbumsUiState.Loaded(List(6) { album("$it", "Album number $it") }))

        composeRule.onNodeWithText("Album number 2").assertIsDisplayed()
    }

    @Test
    fun `an album with no album artist says unknown artist`() {
        show(AlbumsUiState.Loaded(listOf(album("1", "1992 - The Food Album", artist = ""))))

        composeRule.onNodeWithText("Unknown artist").assertIsDisplayed()
    }

    @Test
    fun `the sort button names the current sort`() {
        show(AlbumsUiState.Loaded(listOf(album("1")), sort = AlbumSort.YEAR))

        composeRule.onNodeWithText("Sort: Year").assertIsDisplayed()
    }

    @Test
    fun `choosing a sort from the menu changes it`() {
        var chosen: AlbumSort? = null
        show(AlbumsUiState.Loaded(listOf(album("1"))), onSortChange = { chosen = it })

        composeRule.onNodeWithText("Sort: Title").performClick()
        composeRule.onNodeWithText("Recently added").performClick()

        chosen shouldBe AlbumSort.RECENTLY_ADDED
    }

    @Test
    fun `the sort menu marks the current sort`() {
        show(AlbumsUiState.Loaded(listOf(album("1")), sort = AlbumSort.ARTIST))

        composeRule.onNodeWithText("Sort: Artist").performClick()

        composeRule.onNode(hasText("Artist") and isSelected()).assertExists()
        composeRule.onNode(hasText("Title") and isSelected()).assertDoesNotExist()
    }

    @Test
    fun `an empty library says so`() {
        show(AlbumsUiState.Loaded(emptyList()))

        composeRule.onNodeWithText("No albums found").assertIsDisplayed()
    }

    @Test
    fun `an error offers a retry`() {
        var retried = false
        show(AlbumsUiState.Error, onRetry = { retried = true })

        composeRule.onNodeWithText("Couldn't load albums").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        retried shouldBe true
    }

    @Test
    fun `tapping an album opens it`() {
        var opened: Album? = null
        val purpleBird = album("1", "The Purple Bird")
        show(AlbumsUiState.Loaded(listOf(purpleBird)), onAlbumClick = { opened = it })

        composeRule.onNodeWithText("The Purple Bird").performClick()

        opened shouldBe purpleBird
    }
}
