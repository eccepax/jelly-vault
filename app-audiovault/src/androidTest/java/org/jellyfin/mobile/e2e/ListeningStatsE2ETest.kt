package org.jellyfin.mobile.e2e

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.jellyfin.mobile.R
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.Test

/**
 * The listening stats open from Settings. Listening is kept on the device, cleared between tests,
 * so they start with nothing listened.
 */
@AccountArea
class ListeningStatsE2ETest : LoggedInE2ETest() {

    @Test
    fun theListeningStatsOpenFromSettings() {
        loginAndWaitForLibrary()
        chooseFromLibraryMenu(context.getString(R.string.activity_name_settings))

        composeRule.onNodeWithText(context.getString(R.string.listening_stats)).performScrollTo().performClick()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasText(context.getString(R.string.stats_nothing_yet)),
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
