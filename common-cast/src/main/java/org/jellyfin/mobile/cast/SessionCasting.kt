package org.jellyfin.mobile.cast

import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.jellyfin.mobile.player.session.playableUri
import timber.log.Timber

/**
 * Puts a playback service's session on the cast device while one is connected, and back on the
 * service's own player after (see [SessionPlayerSwitcher]). The service releases its own player
 * and this, not the session's player, which may be either. Until the Cast framework has started
 * (see [casting]) the session stays on the service's player.
 */
@OptIn(UnstableApi::class)
class SessionCasting private constructor(private val casting: Casting) {

    private var player: CastQueuePlayer? = null
    private var switcher: SessionPlayerSwitcher? = null
    private var released = false
    private var waiting: (() -> Unit)? = null
    private var forgetting: (() -> Unit)? = null

    /** Where songs' sources are looked for; what's still being looked for stops with the service. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Released while playback is on the cast device (the service stopping, say after the app is
     * swiped away while paused), the cast ends too: nothing could control it, and the next service
     * wouldn't know the queue it holds. Released before the Cast framework has started, nothing of
     * the service is kept waiting for it.
     */
    fun release() {
        released = true
        scope.cancel()
        waiting?.invoke()
        forgetting?.invoke()
        val player = player ?: return
        if (switcher?.player === player) casting.stopCasting()
        player.release()
    }

    private fun start(device: CastDevice, castPlayers: CastPlayers, session: MediaSession, local: Player, wrap: (Player) -> Player, sources: CastSourceFinder) {
        // Skips go as far there as here (AudioVault's 10 s back and 30 s forward, say)
        val player = CastQueuePlayer(
            device,
            sources,
            seekBackMs = local.seekBackIncrement,
            seekForwardMs = local.seekForwardIncrement,
        )
        this.player = player
        forgetting = castPlayers.forgetsWithCast(player)
        val switcher = SessionPlayerSwitcher(local, wrap, setSessionPlayer = session::setPlayer)
        this.switcher = switcher
        player.onConnectionChange = { connected -> switcher.switchTo(if (connected) player else local) }
        // The service can start while a cast session is already up
        if (device.isConnected) switcher.switchTo(player)
    }

    /**
     * Each song's source as [castSource] finds it (on the main thread, and it may ask the server);
     * one it finds nothing for, or fails on, goes as [defaultCastStream] says.
     */
    private fun finder(
        castSource: (suspend (MediaItem) -> CastSource?)?,
        castUri: (Uri) -> Uri,
        fallbackMimeType: String,
    ) = CastSourceFinder { item, found ->
        val fallback = CastSource { defaultCastStream(item, castUri, fallbackMimeType) }
        if (castSource == null) return@CastSourceFinder found(fallback)
        scope.launch {
            // Whatever goes wrong finding it (the server, say), the song still goes somehow
            @Suppress("TooGenericExceptionCaught")
            val source = try {
                castSource(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "No cast source for %s", item.mediaId)
                null
            }
            found(source ?: fallback)
        }
    }

    companion object {
        /**
         * Null where casting can't work: the session stays on [local]. Each song goes to the cast
         * device from the source [castSource] finds for it, from where it should start; one it says
         * nothing of goes from its own uri made fit to fetch by [castUri], as its own type or
         * [fallbackMimeType] (see [defaultCastStream]). A [live] app's items go as live streams.
         */
        fun attach(
            session: MediaSession,
            local: Player,
            casting: Casting,
            castUri: (Uri) -> Uri = { it },
            fallbackMimeType: String = "audio/mpeg",
            wrap: (Player) -> Player = { it },
            castSource: (suspend (item: MediaItem) -> CastSource?)? = null,
            live: Boolean = false,
        ): SessionCasting? {
            val castPlayers = casting as? CastPlayers ?: return null
            val attached = SessionCasting(casting)
            attached.waiting = castPlayers.whenReady {
                attached.waiting = null
                if (attached.released) return@whenReady
                val sources = attached.finder(castSource, castUri, fallbackMimeType)
                attached.start(castPlayers.newDevice(live), castPlayers, session, local, wrap, sources)
            }
            return attached
        }
    }
}

/**
 * A song as a cast device fetches it when the app says nothing of its stream: its uri (restored
 * from requestMetadata if a controller stripped it) made fit to fetch alone by [castUri] (an app
 * whose player sends the token as a header adds it to the url), as the item's own type or
 * [fallbackMimeType]. An item with no way to play it at all (another controller can add one) goes
 * with its id for a url: the cast device can't play it, but the service carries on.
 */
internal fun defaultCastStream(item: MediaItem, castUri: (Uri) -> Uri, fallbackMimeType: String): CastStream {
    val uri = item.playableUri?.let(castUri) ?: item.mediaId.ifEmpty { "unplayable" }.toUri()
    return CastStream(uri.toString(), item.localConfiguration?.mimeType ?: fallbackMimeType)
}
