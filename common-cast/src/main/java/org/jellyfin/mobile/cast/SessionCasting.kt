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
 * and this, not the session's player, which may be either.
 */
@OptIn(UnstableApi::class)
class SessionCasting private constructor(
    private val castPlayer: CastPlayer,
    private val onCastDevice: () -> Boolean,
    private val casting: Casting,
) {

    /**
     * Released while playback is on the cast device (the service stopping, say after the app is
     * swiped away while paused), the cast ends too: nothing could control it, and the next service
     * wouldn't know the queue it holds.
     */
    fun release() {
        castPlayer.setSessionAvailabilityListener(null)
        if (onCastDevice()) casting.stopCasting()
        castPlayer.release()
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
            val castPlayer = castPlayers.newCastPlayer(VaultMediaItemConverter(castUri, fallbackMimeType, castStream))
            val remote = RemotePlaylist(castPlayer)
            val switcher = SessionPlayerSwitcher(local, wrap, setSessionPlayer = session::setPlayer)
            castPlayer.setSessionAvailabilityListener(
                object : SessionAvailabilityListener {
                    override fun onCastSessionAvailable() = switcher.switchTo(remote)
                    override fun onCastSessionUnavailable() = switcher.switchTo(local)
                },
            )
            // The service can start while a cast session is already up
            if (castPlayer.isCastSessionAvailable) switcher.switchTo(remote)
            return SessionCasting(castPlayer, onCastDevice = { switcher.player !== local }, casting)
        }
    }
}
