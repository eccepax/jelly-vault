package org.jellyfin.mobile.cast

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Moves a media session's playback between players: this device's and a cast device's. The queue,
 * the place, repeat and speed carry over, the player left behind stops, and the session gets the
 * new player through [setSessionPlayer], wrapped by [wrap] the way the app wraps its own player.
 * Playing carries over onto a cast device; back here, playback waits paused rather than starting
 * out of the phone's speaker.
 *
 * A cast player empties as soon as its session ends, before playback can be moved back, so the
 * switcher keeps the playing player's last state and picks up from that (as far on as it would have
 * got, if it was playing). A cast player's queue is read from [RemotePlaylist], as it was sent; a
 * song that still can't say how to play it is left out rather than handed to this device's player,
 * which would crash on it.
 */
class SessionPlayerSwitcher(
    private val local: Player,
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
            PlaybackState.of(this@SessionPlayerSwitcher.player, now())?.let { lastSeen = it }
        }
    }

    init {
        local.addListener(watcher)
    }

    fun switchTo(next: Player) {
        if (next === player) return
        val previous = player
        val state = (PlaybackState.of(previous, now()) ?: lastSeen?.after(now()))?.playable()
        previous.removeListener(watcher)
        previous.stop()
        player = next
        lastSeen = null
        if (state != null) {
            next.setMediaItems(state.items, state.index, state.positionMs)
            next.repeatMode = state.repeatMode
            next.playbackParameters = state.playbackParameters
            next.playWhenReady = state.playWhenReady && next !== local
            next.prepare()
        } else if (next === local) {
            // Nothing to pick up: this device's player still holds the queue from before casting
            next.clearMediaItems()
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

        /**
         * Only the songs a player can play: each needs a uri (restored from requestMetadata if a
         * controller stripped it). If the current song goes, playback starts at the next one kept.
         * Null if none are left.
         */
        fun playable(): PlaybackState? {
            val kept = items.mapIndexedNotNull { i, item -> item.withUri()?.let { i to it } }
            if (kept.isEmpty()) return null
            val newIndex = kept.indexOfFirst { (i, _) -> i >= index }.takeIf { it >= 0 } ?: kept.lastIndex
            val sameSong = kept[newIndex].first == index
            return copy(
                items = kept.map { it.second },
                index = newIndex,
                positionMs = if (sameSong) positionMs else 0L,
            )
        }

        private fun MediaItem.withUri(): MediaItem? = when {
            localConfiguration != null -> this
            requestMetadata.mediaUri != null -> buildUpon().setUri(requestMetadata.mediaUri).build()
            else -> null
        }

        companion object {
            @OptIn(UnstableApi::class)
            fun of(player: Player, nowMs: Long): PlaybackState? {
                if (player.mediaItemCount == 0) return null
                val sent = (player as? RemotePlaylist)?.items
                return PlaybackState(
                    items = sent ?: List(player.mediaItemCount, player::getMediaItemAt),
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
