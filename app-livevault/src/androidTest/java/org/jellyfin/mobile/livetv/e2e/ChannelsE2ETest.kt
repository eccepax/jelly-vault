package org.jellyfin.mobile.livetv.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import org.jellyfin.mobile.livetv.LiveMainActivity
import org.jellyfin.mobile.livetv.channels.ChannelsScreenTags
import org.jellyfin.mobile.testing.VaultFixtures.LiveTv
import org.jellyfin.mobile.testing.endtoend.SignedInE2ETest
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.Test

/**
 * The channel browser against the test server. Never opens a channel (that would play a stream).
 * The list and chip row are lazy, so only what's on screen exists: tests search or scroll rather
 * than expect a channel further down the list to be there.
 */
class ChannelsE2ETest : SignedInE2ETest<LiveMainActivity>(LiveMainActivity::class.java) {

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
    fun signingInShowsTheVaultsChannels() {
        connectAndSignIn()

        waitForChannels()
    }

    @Test
    fun searchingFindsAChannel() {
        connectAndSignIn()
        waitForChannels()

        composeRule.onNodeWithTag(ChannelsScreenTags.SEARCH).performTextInput(LiveTv.NON_FREE_SEARCH_TERM)

        // A card (clickable, so it merges its texts), not just the search box holding the same text.
        val card = anyChannel and hasText(LiveTv.NON_FREE_CHANNEL)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { composeRule.onAllNodes(card).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aTagChipNarrowsTheChannels() {
        connectAndSignIn()
        waitForChannels()

        composeRule.onNodeWithTag(ChannelsScreenTags.CHIPS).performScrollToNode(hasText(LiveTv.FREE_TAG))
        composeRule.onNodeWithText(LiveTv.FREE_TAG).performClick()
        composeRule.onNodeWithText(LiveTv.FREE_TAG).assertIsSelected()
        // That channel isn't free, so searching for it within Free finds nothing.
        composeRule.onNodeWithTag(ChannelsScreenTags.SEARCH).performTextInput(LiveTv.NON_FREE_SEARCH_TERM)

        waitForComposeText("No channels match \"${LiveTv.NON_FREE_SEARCH_TERM}\"")
    }
}
