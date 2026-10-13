package org.jellyfin.mobile.music.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Test

/** Albums grid -> one album's tracks, against the real Vault. */
@BrowseArea
class AlbumDetailE2ETest : LoggedInE2ETest() {

    private fun openPurpleBird() {
        connectAndLoginToLibrary()
        openAlbum("The Purple Bird", firstTrack = "Turned To Dust (Rolling On)")
    }

    @Smoke
    @Test
    fun openingAnAlbumListsItsTracksInOrder() {
        openPurpleBird()

        composeRule.onNodeWithText("Turned To Dust (Rolling On)").assertIsDisplayed()
        composeRule.onNodeWithText("London May").assertIsDisplayed()
        composeRule.onNodeWithText("12 tracks", substring = true).assertIsDisplayed()
    }

    @Test
    fun backReturnsToTheGridWhereItWas() {
        openPurpleBird()

        composeRule.onNodeWithContentDescription("Back").performClick()

        // The grid kept its scroll position, so the album just opened is still on screen.
        waitForComposeText("The Purple Bird")
        composeRule.onNodeWithText("The Purple Bird").assertIsDisplayed()
    }

    @Test
    fun rotationKeepsTheAlbumOpen() {
        openPurpleBird()

        composeRule.activityRule.scenario.recreate()

        waitForComposeText("Turned To Dust (Rolling On)")
        composeRule.onNodeWithText("Turned To Dust (Rolling On)").assertIsDisplayed()
    }
}
