package org.jellyfin.mobile.music.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.pressBack
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Search tab against the real Vault. */
@BrowseArea
class SearchE2ETest : LoggedInE2ETest() {

    private fun openSearch() {
        connectAndLogin()
        waitForComposeText("Search")
        composeRule.onNodeWithText("Search").performClick()
        waitForComposeText("Search your music")
    }

    private fun type(text: String) {
        composeRule.onNode(hasSetTextAction()).performTextInput(text)
    }

    @Test
    fun aSearchFindsAnAlbumThatOpensAndBackKeepsTheResults() {
        openSearch()
        type("purple")

        waitForComposeText("The Purple Bird")
        composeRule.onNodeWithText("The Purple Bird").performClick()
        waitForComposeText("Turned To Dust (Rolling On)")

        pressBack()

        waitForComposeText("The Purple Bird")
        composeRule.onNode(hasSetTextAction() and hasText("purple")).assertIsDisplayed()
    }

    @Smoke
    @Test
    fun aSongFoundBySearchPlays() {
        openSearch()
        type("London May")
        // The field also holds "London May"; the song row is the other node.
        val songRow = hasText("London May") and !hasSetTextAction()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { composeRule.onAllNodes(songRow).fetchSemanticsNodes().isNotEmpty() }

        composeRule.onNode(songRow).performClick()

        val playback: PlaybackController = koinGet()
        composeRule.waitUntil(30_000L) { onMain { playback.state.value.isPlaying && playback.positionMs > 500L } }
        onMain { assertEquals("London May", playback.state.value.currentTrack?.title) }
    }

    @Test
    fun theLibraryTabKeepsItsPlaceWhileYouSearch() {
        connectAndLoginToLibrary()
        waitForComposeText("Artists")
        composeRule.onNodeWithText("Artists").performClick()
        scrollToAndClick("Kendrick Lamar")
        waitForComposeText("Damn")

        composeRule.onNodeWithText("Search").performClick()
        waitForComposeText("Search your music")
        composeRule.onNodeWithText("Library").performClick()

        waitForComposeText("Damn")
        composeRule.onNodeWithText("Kendrick Lamar").assertIsDisplayed()
    }

    @Test
    fun backFromSearchReturnsHome() {
        openSearch()

        pressBack()

        waitForComposeText("Recently played")
        composeRule.onNodeWithText("Search your music").assertDoesNotExist()
    }
}
