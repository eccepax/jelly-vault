package org.jellyfin.mobile.e2e

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The player's speed button shows the speed in use and opens the choice of speeds, matched by
 * their text ("1.5x"). Verifies the saved result via [koinGet], which is what the next book reads.
 */
@PlaybackArea
class PlaybackSpeedE2ETest : LoggedInE2ETest() {

    @Test
    fun selectingAPlaybackSpeedPersistsIt() {
        loginAndWaitForLibrary()
        startPlaybackFromFirstLibraryItem()

        // The speed in use shows on its own button, which opens the choice of speeds
        waitForComposeText("1.0x")
        composeRule.onNodeWithText("1.0x").performClick()
        composeRule.onNodeWithText("1.5x").performClick()

        assertEquals(1.5f, koinGet<AudioVaultAppPreferences>().playbackSpeed)
    }
}
