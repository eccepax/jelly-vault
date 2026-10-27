package org.jellyfin.mobile.cast

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import org.jellyfin.mobile.player.session.playableUri
import org.jellyfin.mobile.player.session.withPlayableUri

/**
 * Moves a media session's playback between players: this device's and a cast device's. The queue,
 * the place, repeat and speed carry over, the player left behind stops, and the session gets the
 * new player through [setSessionPlayer], wrapped by [wrap] the way the app wraps its own player.
 * Playing carries over onto a cast device; back here, playback waits paused rather than starting
 * out of the phone's speaker.
 *
 * A cast player can't be trusted once its session ends, before playback can be moved back: it
 * empties, or reads back the start of its queue. So leaving a cast device, the switcher picks up from
 * the last state that player reported (as far on as it would have got, if it was playing). A cast
 * player's queue is read from [RemotePlaylist], as it was sent; a song that still can't say how to
 * play it is left out rather than handed to this device's player, which would crash on it.
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
            // Only a cast player's is needed: this device's is read as playback leaves it
            val playing = this@SessionPlayerSwitcher.player
            if (playing !== local) PlaybackState.of(playing, now())?.let { lastSeen = it }
        }
    }

    init {
        local.addListener(watcher)
    }

    fun switchTo(next: Player) {
        if (next === player) return
        val previous = player
        val live = PlaybackState.of(previous, now())
        val state = (if (previous === local) live else lastSeen?.after(now()) ?: live)?.playable()
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
        /** How long the song at [index] lasts, as the player said; C.TIME_UNSET if it didn't. */
        val durationMs: Long,
    ) {
        /**
         * Where playback would be by [nowMs], if it was playing: further on at its speed, through the
         * songs whose lengths are known (the player's for this one, the metadata's for the rest), and
         * as repeat would take it. After the last song, at its end.
         */
        fun after(nowMs: Long): PlaybackState {
            if (!isPlaying || index !in items.indices) return this
            var index = index
            var position = positionMs + ((nowMs - seenAtMs) * playbackParameters.speed).toLong()
            var duration = durationMs
            while (duration > 0 && position >= duration) {
                val next = when {
                    repeatMode == Player.REPEAT_MODE_ONE -> index
                    index < items.lastIndex -> index + 1
                    repeatMode == Player.REPEAT_MODE_ALL -> 0
                    else -> null
                }
                if (next == null) {
                    position = duration
                    break
                }
                position -= duration
                index = next
                duration = items.getOrNull(index)?.mediaMetadata?.durationMs ?: C.TIME_UNSET
            }
            return copy(index = index, positionMs = position, seenAtMs = nowMs, durationMs = duration)
        }

        /**
         * Only the songs a player can play: each needs a uri (restored from requestMetadata if a
         * controller stripped it). If the current song goes, playback starts at the next one kept.
         * Null if none are left.
         */
        fun playable(): PlaybackState? {
            val kept = items.mapIndexedNotNull { i, item -> item.playableUri?.let { i to item.withPlayableUri() } }
            if (kept.isEmpty()) return null
            val newIndex = kept.indexOfFirst { (i, _) -> i >= index }.takeIf { it >= 0 } ?: kept.lastIndex
            val sameSong = kept[newIndex].first == index
            return copy(
                items = kept.map { it.second },
                index = newIndex,
                positionMs = if (sameSong) positionMs else 0L,
            )
        }

        companion object {
            @OptIn(UnstableApi::class)
            fun of(player: Player, nowMs: Long): PlaybackState? {
                if (player.mediaItemCount == 0) return null
                // As sent, unless it doesn't know the queue (this app started while casting)
                val sent = (player as? RemotePlaylist)?.items?.takeIf { it.size == player.mediaItemCount }
                return PlaybackState(
                    items = sent ?: List(player.mediaItemCount, player::getMediaItemAt),
                    index = player.currentMediaItemIndex,
                    positionMs = player.currentPosition,
                    playWhenReady = player.playWhenReady && player.playbackState != Player.STATE_ENDED,
                    repeatMode = player.repeatMode,
                    playbackParameters = player.playbackParameters,
                    isPlaying = player.isPlaying,
                    seenAtMs = nowMs,
                    durationMs = player.duration,
                )
            }
        }
    }
}
