package org.jellyfin.mobile.cast

import androidx.annotation.OptIn
import androidx.media3.common.DeviceInfo
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaStatus

/**
 * Hears the cast device stop because it couldn't play what it was sent, from the statuses the Cast
 * framework passes on. Media3's cast player never says: it has no player error, and tells no
 * listener of one, so a failed stream would look like one stopped there. Told [failed] once each
 * time the cast device goes idle by an error, not again for the statuses that repeat it.
 */
internal class CastFailureWatch(private val failed: () -> Unit) {
    private var idleByError = false

    fun onStatus(playerState: Int, idleReason: Int) {
        val nowIdleByError = playerState == MediaStatus.PLAYER_STATE_IDLE && idleReason == MediaStatus.IDLE_REASON_ERROR
        if (nowIdleByError && !idleByError) failed()
        idleByError = nowIdleByError
    }

    /** A new cast device (or none): what the last one said is nothing to this one. */
    fun reset() {
        idleByError = false
    }
}

/**
 * A session's cast player that has a player error when the cast device couldn't play what it was
 * sent ([failedThere]), as ExoPlayer has one here, so the app shows it the same way. Kept until
 * it's prepared again, as ExoPlayer keeps its own, or until playback comes back here.
 */
@OptIn(UnstableApi::class)
internal class CastFailureReporting(player: Player) : ForwardingPlayer(player) {
    private val listeners = mutableListOf<Player.Listener>()
    private var failure: PlaybackException? = null

    /** Run as it's released: takes it off what hears of failures. */
    var onRelease: () -> Unit = {}

    init {
        player.addListener(
            object : Player.Listener {
                override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
                    if (deviceInfo.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE) cleared()
                }
            },
        )
    }

    override fun getPlayerError(): PlaybackException? = failure ?: super.getPlayerError()

    override fun addListener(listener: Player.Listener) {
        listeners += listener
        super.addListener(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners -= listener
        super.removeListener(listener)
    }

    override fun prepare() {
        cleared()
        super.prepare()
    }

    override fun release() {
        onRelease()
        super.release()
    }

    fun failedThere() {
        if (deviceInfo.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE || failure != null) return
        val error = PlaybackException(
            "The cast device couldn't play this",
            null,
            PlaybackException.ERROR_CODE_REMOTE_ERROR,
        )
        failure = error
        told(error)
    }

    private fun cleared() {
        if (failure == null) return
        failure = null
        told(null)
    }

    private fun told(error: PlaybackException?) {
        val events = Player.Events(FlagSet.Builder().add(Player.EVENT_PLAYER_ERROR).build())
        listeners.toList().forEach { listener ->
            listener.onPlayerErrorChanged(error)
            if (error != null) listener.onPlayerError(error)
            listener.onEvents(this, events)
        }
    }
}
