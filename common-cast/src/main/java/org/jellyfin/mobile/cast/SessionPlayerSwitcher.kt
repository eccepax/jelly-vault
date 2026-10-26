package org.jellyfin.mobile.cast

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player

/**
 * Moves a media session's playback between players: this device's and a cast device's. The queue,
 * the place, play or pause, repeat and speed carry over, the player left behind stops, and the
 * session gets the new player through [setSessionPlayer], wrapped by [wrap] the way the app wraps
 * its own player.
 *
 * A cast player empties as soon as its session ends, before playback can be moved back, so the
 * switcher keeps the playing player's last state and picks up from that (as far on as it would have
 * got, if it was playing).
 */
class SessionPlayerSwitcher(
    local: Player,
    private val wrap: (Player) -> Player = { it },
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val setSessionPlayer: (Player) -> Unit,
) {
    /** The player playback is on now. */
    var player: Player = local
        private set

    private var lastSeen: PlaybackState? = null

    private val watcher = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            PlaybackState.of(player, now())?.let { lastSeen = it }
        }
    }

    init {
        local.addListener(watcher)
    }

    fun switchTo(next: Player) {
        if (next === player) return
        val previous = player
        val state = PlaybackState.of(previous, now()) ?: lastSeen?.after(now())
        previous.removeListener(watcher)
        previous.stop()
        player = next
        lastSeen = null
        if (state != null) {
            next.setMediaItems(state.items, state.index, state.positionMs)
            next.repeatMode = state.repeatMode
            next.playbackParameters = state.playbackParameters
            next.playWhenReady = state.playWhenReady
            next.prepare()
        }
        next.addListener(watcher)
        setSessionPlayer(wrap(next))
    }

    private data class PlaybackState(
        val items: List<MediaItem>,
        val index: Int,
        val positionMs: Long,
        val playWhenReady: Boolean,
        val repeatMode: @Player.RepeatMode Int,
        val playbackParameters: PlaybackParameters,
        val isPlaying: Boolean,
        val seenAtMs: Long,
    ) {
        /** Where playback would be by [nowMs]: further on if it was playing. */
        fun after(nowMs: Long): PlaybackState =
            if (isPlaying) copy(positionMs = positionMs + (nowMs - seenAtMs), seenAtMs = nowMs) else this

        companion object {
            fun of(player: Player, nowMs: Long): PlaybackState? {
                if (player.mediaItemCount == 0) return null
                return PlaybackState(
                    items = List(player.mediaItemCount, player::getMediaItemAt),
                    index = player.currentMediaItemIndex,
                    positionMs = player.currentPosition,
                    playWhenReady = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
                    repeatMode = player.repeatMode,
                    playbackParameters = player.playbackParameters,
                    isPlaying = player.isPlaying,
                    seenAtMs = nowMs,
                )
            }
        }
    }
}
