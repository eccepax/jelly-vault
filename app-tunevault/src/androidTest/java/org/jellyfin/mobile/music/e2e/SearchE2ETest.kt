package org.jellyfin.mobile.music.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.pressBack
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.testing.VaultFixtures.PatternSuite
import org.jellyfin.mobile.testing.VaultFixtures.SignalPath
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Search tab against the test server. */
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
        type(PatternSuite.SEARCH_TERM)

        waitForComposeText(PatternSuite.TITLE)
        composeRule.onNodeWithText(PatternSuite.TITLE).performClick()
        waitForComposeText(PatternSuite.FIRST_TRACK)

        pressBack()

        waitForComposeText(PatternSuite.TITLE)
        composeRule.onNode(hasSetTextAction() and hasText(PatternSuite.SEARCH_TERM)).assertIsDisplayed()
    }

    @Smoke
    @Test
    fun aSongFoundBySearchPlays() {
        openSearch()
        type(PatternSuite.KNOWN_SONG)
        // The field also holds the song's title; the song row is the other node.
        val songRow = hasText(PatternSuite.KNOWN_SONG) and !hasSetTextAction()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { composeRule.onAllNodes(songRow).fetchSemanticsNodes().isNotEmpty() }

        composeRule.onNode(songRow).performClick()

        val playback: PlaybackController = koinGet()
        composeRule.waitUntil(30_000L) { onMain { playback.state.value.isPlaying && playback.positionMs > 500L } }
        onMain { assertEquals(PatternSuite.KNOWN_SONG, playback.state.value.currentTrack?.title) }
    }

    @Test
    fun theLibraryTabKeepsItsPlaceWhileYouSearch() {
        connectAndLoginToLibrary()
        waitForComposeText("Artists")
        composeRule.onNodeWithText("Artists").performClick()
        scrollToAndClick(SignalPath.ARTIST)
        waitForComposeText(SignalPath.TITLE)

        composeRule.onNodeWithText("Search").performClick()
        waitForComposeText("Search your music")
        composeRule.onNodeWithText("Library").performClick()

        waitForComposeText(SignalPath.TITLE)
        composeRule.onNodeWithText(SignalPath.ARTIST).assertIsDisplayed()
    }

    @Test
    fun backFromSearchReturnsHome() {
        openSearch()

        pressBack()

        waitForComposeText("Recently played")
        composeRule.onNodeWithText("Search your music").assertDoesNotExist()
    }
}
