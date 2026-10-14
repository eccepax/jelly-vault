package org.jellyfin.mobile.player.interaction

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.jellyfin.mobile.player.PlayerHost
import org.junit.Test

/**
 * Regression coverage for the previously-suppressed voice-search bug: this class never overrode
 * [android.media.session.MediaSession.Callback.onPlayFromSearch], so Assistant / Android Auto
 * "play X" voice commands silently did nothing.
 */
class PlayerMediaSessionCallbackTest {

    private val viewModel = mockk<PlayerHost>(relaxed = true)
    private val callback = PlayerMediaSessionCallback(viewModel)

    @Test
    fun `onPlayFromSearch with a null query (a generic play command) passes an empty string through`() {
        callback.onPlayFromSearch(null, extras = null)

        verify { viewModel.playFromSearch("") }
    }

    @Test
    fun `onSeekTo seeks the current player`() {
        val player = mockk<androidx.media3.exoplayer.ExoPlayer>(relaxed = true)
        every { viewModel.playerOrNull } returns player

        callback.onSeekTo(5_000L)

        verify { player.seekTo(5_000L) }
    }
}
