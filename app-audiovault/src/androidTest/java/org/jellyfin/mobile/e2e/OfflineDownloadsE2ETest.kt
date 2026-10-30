package org.jellyfin.mobile.e2e

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.jellyfin.mobile.R
import org.jellyfin.mobile.testing.endtoend.FIRST_SCREEN_TIMEOUT_MS
import org.junit.Test
import org.jellyfin.mobile.common.R as CommonR

/** The downloads can be listened to before signing in: opened from the connect screen, back returns to it. */
@DownloadsArea
class OfflineDownloadsE2ETest : LoggedInE2ETest() {

    @Test
    fun theDownloadsOpenFromTheConnectScreenAndBackReturnsToIt() {
        val viewDownloads = context.getString(R.string.view_downloads)
        waitForComposeText(viewDownloads, FIRST_SCREEN_TIMEOUT_MS)

        composeRule.onNodeWithText(viewDownloads).performClick()
        waitForComposeText(context.getString(R.string.downloads_empty))

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        waitForComposeText(context.getString(CommonR.string.host_input_hint))
    }
}
