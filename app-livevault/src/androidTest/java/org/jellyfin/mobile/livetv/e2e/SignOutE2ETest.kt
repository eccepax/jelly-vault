package org.jellyfin.mobile.livetv.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.jellyfin.mobile.livetv.LiveMainActivity
import org.jellyfin.mobile.livetv.channels.ChannelsScreenTags
import org.jellyfin.mobile.testing.endtoend.SignedInE2ETest
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.jellyfin.sdk.api.client.ApiClient
import org.junit.Assert.assertNull
import org.junit.Test
import org.jellyfin.mobile.common.R as CommonR

/** Signing out of LiveVault on the real Vault. Never opens a channel. */
class SignOutE2ETest : SignedInE2ETest<LiveMainActivity>(LiveMainActivity::class.java) {

    private val anyChannel = SemanticsMatcher("is a channel card") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(ChannelsScreenTags.CHANNEL_PREFIX) == true
    }

    override fun clearAppState() {
        clearLiveVaultState()
    }

    private fun waitForChannels() {
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { composeRule.onAllNodes(anyChannel).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun signingOutReturnsToSignInAndYouCanSignBackIn() {
        connectAndSignIn()
        waitForChannels()

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Sign out").performClick()
        composeRule.onNodeWithText("Yes, sign out").performClick()

        waitForView(withId(CommonR.id.editUsername))
        assertNull(koinGet<ApiClient>().accessToken)

        signIn()
        waitForChannels()
    }
}
