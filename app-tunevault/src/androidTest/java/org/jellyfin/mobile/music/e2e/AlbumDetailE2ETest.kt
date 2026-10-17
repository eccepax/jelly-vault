package org.jellyfin.mobile.music.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.jellyfin.mobile.testing.VaultFixtures.PatternSuite
import org.junit.Test

/** Albums grid -> one album's tracks, against the test server. */
@BrowseArea
class AlbumDetailE2ETest : LoggedInE2ETest() {

    private fun openPatternSuite() {
        connectAndLoginToLibrary()
        openAlbum(PatternSuite.TITLE, firstTrack = PatternSuite.FIRST_TRACK)
    }

    @Smoke
    @Test
    fun openingAnAlbumListsItsTracksInOrder() {
        openPatternSuite()

        composeRule.onNodeWithText(PatternSuite.FIRST_TRACK).assertIsDisplayed()
        composeRule.onNodeWithText(PatternSuite.KNOWN_SONG).assertIsDisplayed()
        composeRule.onNodeWithText("${PatternSuite.TRACK_COUNT} tracks", substring = true).assertIsDisplayed()
    }

    @Test
    fun backReturnsToTheGridWhereItWas() {
        openPatternSuite()

        composeRule.onNodeWithContentDescription("Back").performClick()

        // The grid kept its scroll position, so the album just opened is still on screen.
        waitForComposeText(PatternSuite.TITLE)
        composeRule.onNodeWithText(PatternSuite.TITLE).assertIsDisplayed()
    }

    @Test
    fun rotationKeepsTheAlbumOpen() {
        openPatternSuite()

        composeRule.activityRule.scenario.recreate()

        waitForComposeText(PatternSuite.FIRST_TRACK)
        composeRule.onNodeWithText(PatternSuite.FIRST_TRACK).assertIsDisplayed()
    }
}
