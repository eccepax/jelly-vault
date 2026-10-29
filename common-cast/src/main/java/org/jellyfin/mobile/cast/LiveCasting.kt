package org.jellyfin.mobile.cast

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
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

    /** Whether the cast device is playing what it was sent right now: not while it loads or stalls. */
    val started: StateFlow<Boolean>

    /**
     * Sends [stream] to the connected cast device, showing [title], [subtitle] and [artworkUri] there.
     * With none to send it to (it went, or casting hasn't started), it's as if it couldn't play it.
     */
    fun play(stream: CastStream, title: String, subtitle: String?, artworkUri: String?)

    fun stop()

    /**
     * [onError] is told when the cast device can't play what it was sent; [onStopped] when it
     * stops playing it while still connected (its remote's stop, another app casting, the stream
     * ending), so the app can free the stream.
     */
    fun listenWith(onError: (() -> Unit)?, onStopped: (() -> Unit)? = null)

    /** Ends the cast (see [Casting.stopCasting]). */
    fun stopCasting()

    /** For good: a cast that's connected ends too, since nothing could control it after. */
    fun release()
}

/** Casting live streams through [casting]; never connected where casting can't work. */
@OptIn(UnstableApi::class)
fun liveCasting(casting: Casting): LiveCasting {
    val castPlayers = casting as? CastPlayers ?: return NoLiveCasting
    val live = PlayerLiveCasting(casting.castDevice, endCast = casting::stopCasting)
    live.waiting = castPlayers.whenReady {
        live.waiting = null
        val converter = VaultMediaItemConverter(
            castStream = { item -> defaultCastStream(item, { it }, MimeTypes.APPLICATION_M3U8) },
            live = true,
        )
        val (castPlayer, connected) = castPlayers.remoteCastPlayer(converter, live::onConnectionChanged)
            .let { it.player to it.connected }
        if (live.start(castPlayer, connected)) {
            // Signing out empties it, since the stream url it holds carries the token, and what played
            // there is then over (see forgotten); ending the cast from here (its own Stop casting) isn't
            // a stop on the cast device
            live.forgetting = castPlayers.forgetsWithCast(
                castPlayer,
                endingHere = live::endingHere,
                forgotten = live::forgotten,
            )
        } else {
            castPlayer.release()
        }
    }
    return live
}

private object NoLiveCasting : LiveCasting {
    override val connected: StateFlow<Boolean> = MutableStateFlow(false)
    override val started: StateFlow<Boolean> = MutableStateFlow(false)
    override val deviceName: StateFlow<String?> = MutableStateFlow(null)
    override fun play(stream: CastStream, title: String, subtitle: String?, artworkUri: String?) = Unit
    override fun stop() = Unit
    override fun listenWith(onError: (() -> Unit)?, onStopped: (() -> Unit)?) = Unit
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
    private var onStopped: (() -> Unit)? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Bumped by every play and stop, so a play with nowhere to go is only told of while it's the last. */
    private var plays = 0

    /**
     * Whether a stream was sent and hasn't been stopped from here: one that stops is stopped there.
     * Not once the cast is being ended from here ([endingHere]): the cast device stops on the way out.
     */
    private var sent = false

    /** Takes back the wait for the Cast framework, while it's still waiting. */
    internal var waiting: (() -> Unit)? = null

    /** Takes the cast player off what [Casting.forgetCast] empties. */
    internal var forgetting: (() -> Unit)? = null

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _started = MutableStateFlow(false)
    override val started: StateFlow<Boolean> = _started.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _started.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val stopped = playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED
            if (!sent || !stopped || player?.playerError != null) return
            sent = false
            _started.value = false
            onStopped?.invoke()
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Live playback failed on the cast device")
            sent = false
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
        if (!connected) {
            sent = false
            _started.value = false
        }
    }

    override fun play(stream: CastStream, title: String, subtitle: String?, artworkUri: String?) {
        _started.value = false
        val play = ++plays
        val player = player?.takeIf { connected.value }
        if (player == null) {
            // Told once the caller has done with sending it, so it hears of it as of what's playing
            Timber.w("Nowhere to cast to: the cast device went, or casting hasn't started")
            handler.post { if (play == plays && !released) onError?.invoke() }
            return
        }
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
        sent = true
    }

    override fun stop() {
        plays++
        sent = false
        _started.value = false
        val player = player ?: return
        player.stop()
        player.clearMediaItems()
    }

    override fun listenWith(onError: (() -> Unit)?, onStopped: (() -> Unit)?) {
        this.onError = onError
        this.onStopped = onStopped
    }

    /** The cast is being ended from here: the cast device stopping on the way out isn't the viewer's doing. */
    fun endingHere() {
        sent = false
    }

    /**
     * The cast was forgotten (signing out): what played there is over, as if stopped there, so the app
     * frees its stream and leaves it, rather than opening it here again once the cast has gone.
     */
    fun forgotten() {
        sent = false
        _started.value = false
        onStopped?.invoke()
    }

    // Told itself, not only through the framework: this may not have been handed a player yet
    override fun stopCasting() {
        endingHere()
        endCast()
    }

    override fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
        waiting?.invoke()
        waiting = null
        forgetting?.invoke()
        forgetting = null
        if (connected.value) endCast()
        _connected.value = false
        player?.removeListener(listener)
        player?.release()
        player = null
    }
}
