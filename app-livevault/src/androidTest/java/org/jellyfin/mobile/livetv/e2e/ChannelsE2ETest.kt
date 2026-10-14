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
import org.jellyfin.mobile.livetv.data.LiveDatabase
import org.jellyfin.mobile.testing.endtoend.SignedInE2ETest
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.Test

/**
 * The channel browser against the real Vault. Never opens a channel (that would play a stream).
 * The list and chip row are lazy, so only what's on screen exists: tests search or scroll rather
 * than expect a channel like ESPN (number 43) to be there.
 */
class ChannelsE2ETest : SignedInE2ETest<LiveMainActivity>(LiveMainActivity::class.java) {

    private val anyChannel = SemanticsMatcher("is a channel card") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(ChannelsScreenTags.CHANNEL_PREFIX) == true
    }

    override fun clearAppState() {
        koinGet<LiveDatabase>().clearAllTables()
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

        composeRule.onNodeWithTag(ChannelsScreenTags.SEARCH).performTextInput("ESPN")

        // A card (clickable, so it merges its texts), not just the search box holding the same text.
        val espnCard = anyChannel and hasText("ESPN")
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { composeRule.onAllNodes(espnCard).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aTagChipNarrowsTheChannels() {
        connectAndSignIn()
        waitForChannels()

        composeRule.onNodeWithTag(ChannelsScreenTags.CHIPS).performScrollToNode(hasText("Free"))
        composeRule.onNodeWithText("Free").performClick()
        composeRule.onNodeWithText("Free").assertIsSelected()
        // ESPN isn't a free channel, so searching for it within Free finds nothing.
        composeRule.onNodeWithTag(ChannelsScreenTags.SEARCH).performTextInput("ESPN")

        waitForComposeText("No channels match \"ESPN\"")
    }
}
