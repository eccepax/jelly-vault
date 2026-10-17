package org.jellyfin.mobile.music.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import org.junit.Test

/** Connect -> Login -> lands on Home. Runs against the test server. */
@AccountArea
class LoginFlowE2ETest : LoggedInE2ETest() {

    @Smoke
    @Test
    fun loginThroughToHome() {
        connectAndLogin()

        waitForComposeText("Recently played")
        composeRule.onNodeWithText("Recently played").assertIsDisplayed()
    }

    /**
     * Guards the bug class both sibling apps shipped (see the shared-activity rotation bug): a
     * config change replaying session state must not bounce a signed-in user back to connect/login.
     */
    @Test
    fun rotationKeepsUserSignedIn() {
        connectAndLogin()
        waitForComposeText("Recently played")

        composeRule.activityRule.scenario.recreate()

        waitForComposeText("Recently played")
        composeRule.onNodeWithText("Recently played").assertIsDisplayed()
    }
}
