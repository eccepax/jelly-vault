package org.jellyfin.mobile.player.interaction

import android.media.session.MediaSession
import android.os.Bundle
import org.jellyfin.mobile.player.PlayerHost

class PlayerMediaSessionCallback(private val viewModel: PlayerHost) : MediaSession.Callback() {
    override fun onPlay() {
        viewModel.play()
    }

    override fun onPlayFromSearch(query: String?, extras: Bundle?) {
        viewModel.playFromSearch(query.orEmpty())
    }

    override fun onPause() {
        viewModel.pause()
    }

    override fun onSeekTo(pos: Long) {
        viewModel.playerOrNull?.seekTo(pos)
    }

    override fun onRewind() {
        viewModel.rewind()
    }

    override fun onFastForward() {
        viewModel.fastForward()
    }

    override fun onSkipToPrevious() {
        viewModel.skipToPrevious()
    }

    override fun onSkipToNext() {
        viewModel.skipToNext()
    }

    override fun onStop() {
        viewModel.stop()
    }
}
