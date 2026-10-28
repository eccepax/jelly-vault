package org.jellyfin.mobile.cast

import androidx.annotation.OptIn
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
 * out of the phone's speaker. A stopped player's queue carries over stopped. Back here, the place is
 * set through the app's wrapper too, which knows where this device's streams start (a transcode
 * started part way, say): a cast player's places are always the song's.
 *
 * A cast device's queue is [CastQueuePlayer]'s own, still there once its session has ended, where
 * it had got to; one it emptied leaves nothing to pick up here. With nothing here to cast, a cast
 * player takes up what the cast device is already playing ([CastQueuePlayer.takeUpDevice]).
 *
 * A volume asked for while casting (a sleep timer's fade, say) isn't the cast device's (see
 * [CastQueuePlayer.volumeAskedFor]): back here, it's this device's player's.
 */
// It reads CastQueuePlayer, which is built on Media3's unstable SimpleBasePlayer
@OptIn(UnstableApi::class)
class SessionPlayerSwitcher(
    private val local: Player,
    private val wrap: (Player) -> Player = { it },
    private val setSessionPlayer: (Player) -> Unit,
) {
    /** The player playback is on now. */
    var player: Player = local
        private set

    fun switchTo(next: Player) {
        if (next === player) return
        val previous = player
        val state = PlaybackState.of(previous)?.playable()
        val volume = (previous as? CastQueuePlayer)?.volumeAskedFor
        previous.stop()
        player = next
        if (next === local) volume?.let { local.volume = it }
        val wrapped = wrap(next)
        when {
            state != null -> {
                next.setMediaItems(state.items, state.index, state.positionMs)
                if (next === local && wrapped !== next) wrapped.seekTo(state.index, state.positionMs)
                next.repeatMode = state.repeatMode
                next.playbackParameters = state.playbackParameters
                next.playWhenReady = state.playWhenReady && next !== local
                if (state.prepared) next.prepare()
            }
            // Nothing to pick up: this device's player still holds the queue from before casting
            next === local -> next.clearMediaItems()
            else -> (next as? CastQueuePlayer)?.takeUpDevice()
        }
        setSessionPlayer(wrapped)
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
}
