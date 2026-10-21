package org.jellyfin.mobile.e2e

import org.jellyfin.mobile.player.playback.AudiobookPlaybackController
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A book plays in the playback service, not in the app's screens: closing them (swiping the app
 * away, or Back out of it) leaves it playing. Before the service, the player went with the screen.
 */
class BackgroundPlaybackE2ETest : LoggedInE2ETest() {

    private val playback: AudiobookPlaybackController get() = koinGet()

    private fun position(): Long = onMain { playback.positionMs }

    @Test
    fun aBookKeepsPlayingWithTheAppClosed() {
        loginAndWaitForLibrary()
        startPlaybackFromFirstLibraryItem()
        waitForPlaying()

        composeRule.activityRule.scenario.onActivity { it.finish() }
        val closedAt = position()

        waitUntil { position() >= closedAt + PLAYED_ON_MS }
        assertTrue("still playing with the app closed", onMain { playback.state.value.isPlaying })
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for playback to carry on" }
            Thread.sleep(POLL_MS)
        }
    }

    private companion object {
        /** Long enough that it can't be the last bit buffered before the player went. */
        const val PLAYED_ON_MS = 3_000L
        const val POLL_MS = 200L
    }
}
