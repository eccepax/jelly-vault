package org.jellyfin.mobile.music.e2e

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.espresso.Espresso.pressBack
import org.jellyfin.mobile.music.nowplaying.MiniPlayerTags
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

private const val PLAYBACK_TIMEOUT_MS = 30_000L

/** Queue actions and the queue screen against real playback from the Vault. */
@PlaybackArea
class QueueE2ETest : LoggedInE2ETest() {

    private val playback: PlaybackController get() = koinGet()

    private fun playLondonMay() {
        connectAndLoginToLibrary()
        openAlbum("The Purple Bird", firstTrack = "Turned To Dust (Rolling On)")
        composeRule.onNodeWithText("London May").performClick()
        composeRule.waitUntil(PLAYBACK_TIMEOUT_MS) {
            onMain { playback.state.value.isPlaying && playback.positionMs > 500L }
        }
    }

    private fun upNextTitle(): String? = onMain {
        val queue = playback.state.value.queue
        queue.tracks.getOrNull(queue.currentIndex + 1)?.title
    }

    private fun openQueue() {
        composeRule.onNodeWithTag(MiniPlayerTags.ROOT).performClick()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            runCatching { composeRule.onNodeWithContentDescription("Queue").assertExists() }.isSuccess
        }
        composeRule.onNodeWithContentDescription("Queue").performClick()
        waitForComposeText("Up next")
    }

    @Test
    fun theSongPlayingIsMarkedInTheAlbum() {
        playLondonMay()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeRule.onAllNodes(hasText("London May") and hasContentDescription("Now playing"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun playNextFromALongPressIsUpNext() {
        playLondonMay()

        composeRule.onNodeWithText("Boise, Idaho").performTouchInput { longClick() }
        composeRule.onNodeWithText("Play next").performClick()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) { upNextTitle() == "Boise, Idaho" }
        onMain {
            assertEquals("London May", playback.state.value.currentTrack?.title)
            assertEquals(13, playback.state.value.queue.tracks.size)
        }
    }

    @Test
    fun theQueueScreenRemovesAnUpcomingSongWithoutInterruptingPlayback() {
        playLondonMay()
        openQueue()

        composeRule.onNodeWithContentDescription("Remove Tonight With The Dogs I'm Sleeping").performClick()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) { onMain { playback.state.value.queue.tracks.size == 11 } }
        onMain {
            assertFalse(playback.state.value.queue.tracks.any { it.title == "Tonight With The Dogs I'm Sleeping" })
            assertEquals("London May", playback.state.value.currentTrack?.title)
        }
    }

    @Test
    fun draggingAnUpcomingSongReordersTheQueue() {
        playLondonMay()
        openQueue()
        // The row merges its children into one node; the handle icon itself is in the unmerged tree.
        val handles = composeRule.onAllNodesWithContentDescription("Drag to reorder", useUnmergedTree = true)
        val rowHeight = handles[1].getBoundsInRoot().top - handles[0].getBoundsInRoot().top

        handles[0].performTouchInput {
            down(center)
            repeat(10) { step -> moveTo(center.copy(y = center.y + (rowHeight.toPx() * 2.4f) * (step + 1) / 10)) }
            up()
        }

        // "Tonight With The Dogs…" (first up next) dragged below "Boise, Idaho" and "The Water's Fine".
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            onMain {
                val queue = playback.state.value.queue
                queue.tracks.drop(queue.currentIndex + 1).take(3).map { it.title } ==
                    listOf("Boise, Idaho", "The Water's Fine", "Tonight With The Dogs I'm Sleeping")
            }
        }
        onMain { assertEquals("London May", playback.state.value.currentTrack?.title) }
    }

    @Test
    fun backFromTheQueueReturnsToNowPlaying() {
        playLondonMay()
        openQueue()

        pressBack()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            runCatching { composeRule.onNodeWithContentDescription("Collapse").assertExists() }.isSuccess
        }
    }
}
