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
    fun `onPlayFromSearch delegates the query to the view model`() {
        callback.onPlayFromSearch("harry potter", extras = null)

        verify { viewModel.playFromSearch("harry potter") }
    }

    @Test
    fun `onPlayFromSearch with a null query (a generic play command) passes an empty string through`() {
        callback.onPlayFromSearch(null, extras = null)

        verify { viewModel.playFromSearch("") }
    }

    @Test
    fun `onPlay delegates to the view model`() {
        callback.onPlay()

        verify { viewModel.play() }
    }

    @Test
    fun `onPause delegates to the view model`() {
        callback.onPause()

        verify { viewModel.pause() }
    }

    @Test
    fun `onSeekTo seeks the current player`() {
        val player = mockk<androidx.media3.exoplayer.ExoPlayer>(relaxed = true)
        every { viewModel.playerOrNull } returns player

        callback.onSeekTo(5_000L)

        verify { player.seekTo(5_000L) }
    }

    @Test
    fun `onRewind delegates to the view model`() {
        callback.onRewind()

        verify { viewModel.rewind() }
    }

    @Test
    fun `onFastForward delegates to the view model`() {
        callback.onFastForward()

        verify { viewModel.fastForward() }
    }

    @Test
    fun `onSkipToPrevious delegates to the view model`() {
        callback.onSkipToPrevious()

        verify { viewModel.skipToPrevious() }
    }

    @Test
    fun `onSkipToNext delegates to the view model`() {
        callback.onSkipToNext()

        verify { viewModel.skipToNext() }
    }

    @Test
    fun `onStop delegates to the view model`() {
        callback.onStop()

        verify { viewModel.stop() }
    }
}
