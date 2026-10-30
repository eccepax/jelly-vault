package org.jellyfin.mobile.cast

import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.cast.CastPlayer
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import org.jellyfin.mobile.player.session.playableUri

/**
 * Puts a playback service's session on Media3's [CastPlayer] once the Cast framework has started:
 * one player over the service's own and the cast device, which moves playback between the two as
 * a cast starts and ends. The service releases its own player and this, not the session's player.
 * Until the Cast framework has started (see [casting]) the session stays on the service's player.
 */
@OptIn(UnstableApi::class)
class SessionCasting private constructor(private val casting: Casting) {

    private var player: Player? = null
    private var released = false
    private var waiting: (() -> Unit)? = null
    private var forgetting: (() -> Unit)? = null

    /**
     * Released while playback is on the cast device (the service stopping, say after the app is
     * swiped away while paused), the cast ends too: nothing could control it, and the next service
     * wouldn't know the queue it holds.
     */
    fun release() {
        released = true
        waiting?.invoke()
        forgetting?.invoke()
        val player = player ?: return
        if (player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) casting.stopCasting()
        player.release()
    }

    companion object {
        /**
         * Null where casting can't work: the session stays on [local]. Each song goes to the cast
         * device as [castStream] says; one it says nothing of goes from its own uri made fit to
         * fetch by [castUri], as its own type or [fallbackMimeType] (see [defaultCastStream]).
         */
        fun attach(
            session: MediaSession,
            local: Player,
            casting: Casting,
            castUri: (Uri) -> Uri = { it },
            fallbackMimeType: String = "audio/mpeg",
            wrap: (Player) -> Player = { it },
            castStream: ((item: MediaItem) -> CastStream?)? = null,
        ): SessionCasting? {
            val castPlayers = casting as? CastPlayers ?: return null
            val attached = SessionCasting(casting)
            attached.waiting = castPlayers.whenReady {
                attached.waiting = null
                if (attached.released) return@whenReady
                val converter = VaultMediaItemConverter(
                    castStream = { item -> castStream?.invoke(item) ?: defaultCastStream(item, castUri, fallbackMimeType) },
                )
                val player = castPlayers.castPlayer(local, converter)
                attached.player = player
                attached.forgetting = castPlayers.forgetsWithCast(player)
                session.player = wrap(CastQueueCap(player))
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
