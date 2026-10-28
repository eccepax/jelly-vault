package org.jellyfin.mobile.cast

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * One live stream at a time on the cast device, for an app that plays without a media session
 * (LiveVault). The app opens and closes the stream on the server; the cast device only plays it,
 * sent as a live stream. Main thread only, like the Cast framework.
 */
interface LiveCasting {
    /** Whether a cast device is connected: what's played goes there. */
    val connected: StateFlow<Boolean>

    /** The name of the cast device connected; null while none is. */
    val deviceName: StateFlow<String?>

    /** Whether the cast device is actually playing what it was sent; reset by [play] and [stop]. */
    val started: StateFlow<Boolean>

    /** Sends [stream] to the connected cast device, showing [title], [subtitle] and [artworkUri] there. */
    fun play(stream: CastStream, title: String, subtitle: String?, artworkUri: String?)

    fun stop()

    /** [onError] is told when the cast device can't play what it was sent. */
    fun listenWith(onError: (() -> Unit)?)

    /** Ends the cast (see [Casting.stopCasting]). */
    fun stopCasting()

    /** For good: a cast that's connected ends too, since nothing could control it after. */
    fun release()
}

private const val HLS_MIME_TYPE = "application/x-mpegURL"

/** Casting live streams through [casting]; never connected where casting can't work. */
@OptIn(UnstableApi::class)
fun liveCasting(casting: Casting): LiveCasting {
    val castPlayers = casting as? CastPlayers ?: return NoLiveCasting
    val live = PlayerLiveCasting(casting.castDevice, endCast = casting::stopCasting)
    castPlayers.whenReady {
        val device = castPlayers.newDevice(live = true)
        val castPlayer = CastQueuePlayer(
            device,
            streamFor = { item, _ -> defaultCastStream(item, { it }, HLS_MIME_TYPE) },
        )
        castPlayer.onConnectionChange = live::onConnectionChanged
        if (!live.start(castPlayer, device.isConnected)) castPlayer.release()
    }
    return live
}

private object NoLiveCasting : LiveCasting {
    override val connected: StateFlow<Boolean> = MutableStateFlow(false)
    override val started: StateFlow<Boolean> = MutableStateFlow(false)
    override val deviceName: StateFlow<String?> = MutableStateFlow(null)
    override fun play(stream: CastStream, title: String, subtitle: String?, artworkUri: String?) = Unit
    override fun stop() = Unit
    override fun listenWith(onError: (() -> Unit)?) = Unit
    override fun stopCasting() = Unit
    override fun release() = Unit
}

/** [LiveCasting] over the cast [player] it's given once the Cast framework has started. */
internal class PlayerLiveCasting(
    override val deviceName: StateFlow<String?>,
    private val endCast: () -> Unit,
) : LiveCasting {
    private var player: Player? = null
    private var released = false
    private var onError: (() -> Unit)? = null

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _started = MutableStateFlow(false)
    override val started: StateFlow<Boolean> = _started.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) _started.value = true
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Live playback failed on the cast device")
            onError?.invoke()
        }
    }

    /** False once released: the caller releases [player] itself then. */
    fun start(player: Player, connected: Boolean): Boolean {
        if (released) return false
        this.player = player
        player.addListener(listener)
        _connected.value = connected
        return true
    }

    fun onConnectionChanged(connected: Boolean) {
        _connected.value = connected
        if (!connected) _started.value = false
    }

    override fun play(stream: CastStream, title: String, subtitle: String?, artworkUri: String?) {
        _started.value = false
        val player = player?.takeIf { connected.value } ?: return
        val item = MediaItem.Builder()
            .setUri(stream.url)
            .setMimeType(stream.mimeType)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setArtworkUri(artworkUri?.let(Uri::parse))
                    .build(),
            )
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    override fun stop() {
        _started.value = false
        val player = player ?: return
        player.stop()
        player.clearMediaItems()
    }

    override fun listenWith(onError: (() -> Unit)?) {
        this.onError = onError
    }

    override fun stopCasting() = endCast()

    override fun release() {
        released = true
        if (connected.value) endCast()
        _connected.value = false
        player?.removeListener(listener)
        player?.release()
        player = null
    }
}
