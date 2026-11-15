package org.jellyfin.mobile.widget

import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

/** The widget's buttons, as they reach the playback service's player. */
class WidgetCommandTest {

    private val player = mockk<Player>(relaxed = true)

    @Test
    fun `play-pause pauses a book playing`() {
        every { player.playWhenReady } returns true

        WidgetCommand.PLAY_PAUSE.perform(player)

        verify { player.pause() }
    }

    @Test
    fun `play-pause plays a book paused, after loading it again if it had stopped`() {
        every { player.playWhenReady } returns false
        every { player.playbackState } returns Player.STATE_IDLE

        WidgetCommand.PLAY_PAUSE.perform(player)

        verify { player.prepare() }
        verify { player.play() }
    }

    @Test
    fun `back goes back 10 seconds`() {
        WidgetCommand.REWIND.perform(player)

        verify { player.seekBack() }
    }
}
