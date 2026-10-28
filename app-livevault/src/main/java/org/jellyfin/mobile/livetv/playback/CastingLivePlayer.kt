package org.jellyfin.mobile.livetv.playback

import androidx.media3.common.MimeTypes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import org.jellyfin.mobile.cast.CastStream
import org.jellyfin.mobile.cast.LiveCasting
import org.jellyfin.mobile.livetv.channels.displayName
import org.jellyfin.mobile.livetv.domain.LiveChannel

/**
 * Plays a channel on the cast device while one is connected ([cast]), else here ([local]). The
 * stream must be opened for where it plays (see [LivePlayer.onCastDevice]); on the cast device it
 * shows as the channel [describe] gives (number, name and logo). Not what's on: that's sent once,
 * with the stream, so it would still show the program before long after it ended.
 */
class CastingLivePlayer(
    private val local: LivePlayer,
    private val cast: LiveCasting,
    private val describe: (channelId: String) -> LiveChannel?,
) : LivePlayer {

    /** Where the channel last played went: errors from anywhere else are stale. */
    private var onCast = false
    private var listener: LivePlayer.Listener? = null

    override val onCastDevice: StateFlow<Boolean> = cast.connected

    @OptIn(ExperimentalCoroutinesApi::class)
    override val videoStarted: Flow<Boolean> =
        cast.connected.flatMapLatest { connected -> if (connected) cast.started else local.videoStarted }

    init {
        local.listenWith { if (!onCast) listener?.onError() }
        cast.listenWith(
            onError = { if (onCast) listener?.onError() },
            onStopped = { if (onCast) listener?.onStoppedElsewhere() },
        )
    }

    override fun play(url: String, channelId: String) {
        onCast = cast.connected.value
        if (onCast) {
            val channel = describe(channelId)
            cast.play(
                CastStream(url, MimeTypes.APPLICATION_M3U8),
                title = channel?.let { "${it.number} ${it.displayName}" } ?: channelId,
                subtitle = null,
                artworkUri = channel?.logoUrl,
            )
        } else {
            local.play(url, channelId)
        }
    }

    override fun stop() {
        if (onCast) cast.stop() else local.stop()
    }

    override fun listenWith(listener: LivePlayer.Listener?) {
        this.listener = listener
    }

    override fun release() {
        local.release()
        cast.release()
    }
}
