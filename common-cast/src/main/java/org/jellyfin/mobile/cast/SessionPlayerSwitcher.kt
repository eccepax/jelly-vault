package org.jellyfin.mobile.cast

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.jellyfin.mobile.player.session.withPlayableUri

/**
 * Moves a media session's playback between players: this device's and a cast device's. The queue,
 * the place, repeat and speed carry over, the player left behind stops, and the session gets the
 * new player through [setSessionPlayer], wrapped by [wrap] the way the app wraps its own player.
 * Everything is read and set through the app's wrappers, which know where the places of their
 * players' streams start (a transcode started part way, say), so a place is always the song's.
 * Every song carries over, so the queue stays the one the app gave, index for index.
 *
 * Playing carries over onto a cast device; back here, playback waits paused rather than starting
 * out of the phone's speaker. Cast again soon after ([CARRY_ON_WITHIN_MS], another cast device
 * picked, say) without having played here meanwhile, it plays on there as it did. A stopped
 * player's queue carries over stopped.
 *
 * A cast device's queue is [CastQueuePlayer]'s own, still there once its session has ended, where
 * it had got to; one it emptied leaves nothing to pick up here. Once it's picked up here the cast
 * player lets it go, so each cast starts afresh. With nothing here to cast, a cast player takes up
 * what the cast device is already playing ([CastQueuePlayer.takeUpDevice]).
 *
 * The session moves to the new player before the one left behind stops, so nothing reading it sees
 * playback stop in the middle of the switch.
 *
 * A volume asked for while casting (a sleep timer's fade, say) isn't the cast device's (see
 * [CastQueuePlayer.volumeAskedFor]): back here, it's this device's player's. Each cast starts from
 * this device's player's volume.
 */
// It reads CastQueuePlayer, which is built on Media3's unstable SimpleBasePlayer
@OptIn(UnstableApi::class)
class SessionPlayerSwitcher(
    private val local: Player,
    private val wrap: (Player) -> Player = { it },
    private val setSessionPlayer: (Player) -> Unit,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    /** The player playback is on now. */
    var player: Player = local
        private set

    /** When playback came back here paused from a cast device that was playing; null once it plays here. */
    private var pausedComingBackAt: Long? = null

    init {
        local.addListener(
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (playWhenReady) pausedComingBackAt = null
                }
            },
        )
    }

    fun switchTo(next: Player) {
        if (next === player) return
        val previous = player
        val state = PlaybackState.of(wrap(previous))?.withUris()
        val volume = (previous as? CastQueuePlayer)?.volumeAskedFor
        val carryOn = pausedComingBackAt?.let { now() - it <= CARRY_ON_WITHIN_MS } == true
        pausedComingBackAt = if (next === local && state?.playWhenReady == true) now() else null
        player = next
        // Each cast starts from this device's volume, so one asked for in an earlier cast never comes back
        if (next === local) volume?.let { local.volume = it } else next.volume = local.volume
        val wrapped = wrap(next)
        when {
            state != null -> {
                wrapped.setMediaItems(state.items, state.index, state.positionMs)
                wrapped.repeatMode = state.repeatMode
                wrapped.playbackParameters = state.playbackParameters
                wrapped.playWhenReady = next !== local && (state.playWhenReady || carryOn)
                if (state.prepared) wrapped.prepare()
            }
            // Nothing to pick up: this device's player still holds the queue from before casting
            next === local -> next.clearMediaItems()
            else -> (next as? CastQueuePlayer)?.takeUpDevice()
        }
        // Only once the session is on the new player, so nothing reading it sees playback stop mid-switch
        setSessionPlayer(wrapped)
        previous.stop()
        // What it held is here now: the next cast starts from what's here then, not from this
        if (previous is CastQueuePlayer) previous.clearMediaItems()
    }

    private data class PlaybackState(
        val items: List<MediaItem>,
        val index: Int,
        val positionMs: Long,
        val playWhenReady: Boolean,
        val prepared: Boolean,
        val repeatMode: @Player.RepeatMode Int,
        val playbackParameters: PlaybackParameters,
    ) {
        /**
         * Every song with a uri, as a player needs (restored from requestMetadata if a controller
         * stripped it). One with no way to play it at all (another controller can add one) gets a
         * uri that leads nowhere: playing it fails when it's reached, and the songs keep their places.
         */
        fun withUris(): PlaybackState = copy(
            items = items.map { item ->
                item.withPlayableUri().takeIf { it.localConfiguration != null }
                    ?: item.buildUpon().setUri("$NOWHERE${item.mediaId}".toUri()).build()
            },
        )

        companion object {
            fun of(player: Player): PlaybackState? {
                if (player.mediaItemCount == 0) return null
                return PlaybackState(
                    items = List(player.mediaItemCount, player::getMediaItemAt),
                    index = player.currentMediaItemIndex,
                    positionMs = player.currentPosition,
                    playWhenReady = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
                    prepared = player.playbackState != Player.STATE_IDLE,
                    repeatMode = player.repeatMode,
                    playbackParameters = player.playbackParameters,
                )
            }
        }
    }

    companion object {
        /** How soon after coming back here a new cast still plays on as the last one did. */
        const val CARRY_ON_WITHIN_MS = 30_000L

        /** The uri of a song with no way to play it. */
        private const val NOWHERE = "unplayable:"
    }
}
