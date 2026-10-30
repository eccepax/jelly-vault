package org.jellyfin.mobile.cast

import androidx.annotation.OptIn
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Another app taking the cast device over: once a song of this app's own ([sent] from here) has
 * played there, a song it didn't send playing there in its place means another app loaded its own.
 * This app then [leave]s the cast, leaving the cast device playing: it shouldn't show the other's
 * song as its own, report listening to it, or take it back here when its cast ends. Joining a cast
 * device that's playing another app's song isn't a takeover: this app's queue may be on its way.
 */
@OptIn(UnstableApi::class)
internal class CastTakeover(
    private val player: Player,
    private val sent: SentItems,
    private val leave: () -> Unit,
) : Player.Listener {
    /** Whether a song of this app's own has played on the cast device since it was cast to. */
    private var ownPlayedThere = false

    init {
        player.addListener(this)
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (this.player.deviceInfo.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE) {
            ownPlayedThere = false
            return
        }
        val playing = this.player.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() } ?: return
        if (sent[playing] != null) {
            ownPlayedThere = true
        } else if (ownPlayedThere) {
            ownPlayedThere = false
            leave()
        }
    }
}
