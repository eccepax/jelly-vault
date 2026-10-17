package org.jellyfin.mobile.livetv.e2e

import android.content.pm.ActivityInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import org.jellyfin.mobile.livetv.LiveMainActivity
import org.jellyfin.mobile.livetv.channels.ChannelsScreenTags
import org.jellyfin.mobile.livetv.playback.PlayerScreenTags
import org.jellyfin.mobile.livetv.playback.StreamLedger
import org.jellyfin.mobile.livetv.playback.StreamTicket
import org.jellyfin.mobile.testing.VaultFixtures.LiveTv
import org.jellyfin.mobile.testing.endtoend.SignedInE2ETest
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Plays real live TV from the server. ONLY free channels: pointed at a real server, anything else
 * could take a provider's limited logins. The ledger of open streams only drops a stream once the server has
 * confirmed it closed, so "the ledger is empty" means nothing is left holding a tuner.
 */
class PlaybackE2ETest : SignedInE2ETest<LiveMainActivity>(LiveMainActivity::class.java) {

    private val anyChannel = SemanticsMatcher("is a channel card") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(ChannelsScreenTags.CHANNEL_PREFIX) == true
    }
    private val ledger get() = koinGet<StreamLedger>()

    override fun clearAppState() {
        clearLiveVaultState()
    }

    @After
    fun nothingLeftOpen() {
        waitUntil("every stream closed") { ledger.all().isEmpty() }
    }

    private fun waitUntil(what: String, timeoutMillis: Long = WAIT_TIMEOUT_MS, condition: () -> Boolean) {
        try {
            composeRule.waitUntil(timeoutMillis, condition)
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("Timed out waiting for: $what", e)
        }
    }

    /** Opens the first channel that is both tagged and named free ([LiveTv.FREE_TAG], [LiveTv.FREE_PREFIX]). */
    private fun watchAFreeChannel() {
        connectAndSignIn()
        waitUntil("channels") { composeRule.onAllNodes(anyChannel).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(ChannelsScreenTags.CHIPS).performScrollToNode(hasText(LiveTv.FREE_TAG))
        composeRule.onNodeWithText(LiveTv.FREE_TAG).performClick()
        composeRule.onNodeWithTag(ChannelsScreenTags.SEARCH).performTextInput(LiveTv.FREE_PREFIX)
        waitUntil("free channels") { composeRule.onAllNodes(anyChannel).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onAllNodes(anyChannel).onFirst().performClick()
    }

    private fun waitForVideo() =
        waitUntil("video playing", VIDEO_TIMEOUT_MS) {
            composeRule.onAllNodes(hasTestTag(PlayerScreenTags.VIDEO_PLAYING)).fetchSemanticsNodes().isNotEmpty()
        }

    /**
     * Back, through the activity: with the system bars hidden, Android shows its full-screen
     * confirmation, which takes window focus and makes Espresso's pressBack wait forever.
     */
    private fun pressBack() {
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
    }

    private fun openStream(): StreamTicket = ledger.all().also { assertEquals("open streams", 1, it.size) }.single()

    @Test
    fun watchingAFreeChannelPlaysItAndBackClosesIt() {
        watchAFreeChannel()

        waitForVideo()
        openStream()

        // Still playing a while later: the server once stopped the transcode seconds in, and
        // the player only failed when its buffer ran out.
        Thread.sleep(KEEPS_PLAYING_MS)
        composeRule.onNodeWithTag(PlayerScreenTags.VIDEO_PLAYING).assertExists()

        pressBack()
        waitUntil("back on the channels") { composeRule.onAllNodes(anyChannel).fetchSemanticsNodes().isNotEmpty() }
        waitUntil("stream closed") { ledger.all().isEmpty() }

        // The channel just watched is offered to continue (once the search is cleared).
        composeRule.onNodeWithTag(ChannelsScreenTags.SEARCH).performTextClearance()
        waitUntil("continue watching") {
            composeRule.onAllNodes(hasTestTag(ChannelsScreenTags.CONTINUE)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun rotatingKeepsTheSameStream() {
        watchAFreeChannel()
        waitForVideo()
        val before = openStream()

        composeRule.activityRule.scenario.onActivity {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        composeRule.waitForIdle()
        waitForVideo()

        assertEquals("the stream from before the rotation", before, openStream())
        pressBack()
    }

    @Test
    fun theAppGoingToTheBackgroundClosesTheStreamAndComingBackTunesAgain() {
        watchAFreeChannel()
        waitForVideo()
        val before = openStream()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        waitUntil("stream closed while in the background") { ledger.all().isEmpty() }

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        waitForVideo()
        // A new open of the same channel: the server reuses the LiveStreamId of a channel's shared
        // tuner stream, but each open gets its own play session.
        assertNotEquals("a newly opened stream", before.playSessionId, openStream().playSessionId)
        pressBack()
    }
}

/** The server takes several seconds to tune, then the player buffers. */
private const val VIDEO_TIMEOUT_MS = 60_000L

/** Longer than the player's buffer, so a stream the server stopped feeding would have failed. */
private const val KEEPS_PLAYING_MS = 40_000L
