package org.jellyfin.mobile.livetv.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/** [LivePlayer] on Media3: plays the server's HLS (or raw MPEG-TS) live stream. */
class ExoLivePlayer(context: Context) : LivePlayer {

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            // handleAudioFocus: pause for calls and other apps' audio.
            true,
        )
        .build()

    private val _videoStarted = MutableStateFlow(false)
    override val videoStarted: StateFlow<Boolean> = _videoStarted.asStateFlow()

    override val onCastDevice: StateFlow<Boolean> = MutableStateFlow(false)

    private var listener: LivePlayer.Listener? = null

    init {
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) _videoStarted.value = true
                }

                override fun onPlayerError(error: PlaybackException) {
                    Timber.e(error, "Live playback failed")
                    listener?.onError()
                }
            },
        )
    }

    override fun play(url: String, channelId: String) {
        _videoStarted.value = false
        val item = MediaItem.Builder()
            .setUri(url)
            .apply { if (".m3u8" in url) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
        exoPlayer.setMediaItem(item)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    override fun stop() {
        _videoStarted.value = false
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
    }

    override fun listenWith(listener: LivePlayer.Listener?) {
        this.listener = listener
    }

    override fun release() {
        exoPlayer.release()
    }
}
