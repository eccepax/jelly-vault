package org.jellyfin.mobile.cast

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession

/**
 * Puts a playback service's session on the cast device while one is connected, and back on the
 * service's own player after (see [SessionPlayerSwitcher]). The service releases its own player
 * and this, not the session's player, which may be either. Until the Cast framework has started
 * (see [casting]) the session stays on the service's player.
 */
@OptIn(UnstableApi::class)
class SessionCasting private constructor(private val casting: Casting) {

    private var castPlayer: CastPlayer? = null
    private var switcher: SessionPlayerSwitcher? = null
    private var released = false

    /**
     * Released while playback is on the cast device (the service stopping, say after the app is
     * swiped away while paused), the cast ends too: nothing could control it, and the next service
     * wouldn't know the queue it holds.
     */
    fun release() {
        released = true
        val castPlayer = castPlayer ?: return
        castPlayer.setSessionAvailabilityListener(null)
        if (switcher?.player is RemotePlaylist) casting.stopCasting()
        castPlayer.release()
    }

    private fun start(
        castPlayer: CastPlayer,
        session: MediaSession,
        local: Player,
        wrap: (Player) -> Player,
    ) {
        this.castPlayer = castPlayer
        val remote = RemotePlaylist(castPlayer)
        val switcher = SessionPlayerSwitcher(local, wrap, setSessionPlayer = session::setPlayer)
        this.switcher = switcher
        castPlayer.setSessionAvailabilityListener(
            object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() = switcher.switchTo(remote)
                override fun onCastSessionUnavailable() = switcher.switchTo(local)
            },
        )
        // The service can start while a cast session is already up
        if (castPlayer.isCastSessionAvailable) switcher.switchTo(remote)
    }

    companion object {
        /**
         * Null where casting can't work: the session stays on [local]. Items go to the cast device
         * as [VaultMediaItemConverter] sends them, with [castUri] and [fallbackMimeType], or
         * [castStream] where it says.
         */
        fun attach(
            session: MediaSession,
            local: Player,
            casting: Casting,
            castUri: (Uri) -> Uri = { it },
            fallbackMimeType: String = "audio/mpeg",
            wrap: (Player) -> Player = { it },
            castStream: ((MediaItem) -> CastStream?)? = null,
        ): SessionCasting? {
            val castPlayers = casting as? CastPlayers ?: return null
            val attached = SessionCasting(casting)
            castPlayers.whenReady {
                if (attached.released) return@whenReady
                val converter = VaultMediaItemConverter(castUri, fallbackMimeType, castStream)
                attached.start(castPlayers.newCastPlayer(converter), session, local, wrap)
            }
            return attached
        }
    }
}
