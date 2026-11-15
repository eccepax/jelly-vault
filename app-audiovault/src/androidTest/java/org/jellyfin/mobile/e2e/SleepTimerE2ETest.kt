package org.jellyfin.mobile.e2e

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.jellyfin.mobile.R
import org.jellyfin.mobile.player.ui.PlayerTags
import org.junit.Test
import org.jellyfin.mobile.common.R as CommonR

/**
 * Player -> Sleep timer -> pick a length. No on-screen player UI shows the running timer's time,
 * so this confirms the choice closes the dialog and playback keeps running afterward. The timer
 * itself is tested in common's SleepTimerTest and AudiobookSleepTimerTargetTest.
 */
@PlaybackArea
class SleepTimerE2ETest : LoggedInE2ETest() {

    @Test
    fun startingASleepTimerDismissesTheDialogAndKeepsPlaybackRunning() {
        loginAndWaitForLibrary()
        startPlaybackFromFirstLibraryItem()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.sleep_timer)).performClick()
        composeRule.onNodeWithText(context.getString(CommonR.string.sleep_15)).performClick()

        composeRule.onNodeWithText(context.getString(CommonR.string.sleep_15)).assertDoesNotExist()
        composeRule.onNodeWithTag(PlayerTags.PLAY_PAUSE).assertExists()
    }
}
