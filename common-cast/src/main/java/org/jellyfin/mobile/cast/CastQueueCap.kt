package org.jellyfin.mobile.cast

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * A session's player that never sends a cast device more than [CAST_QUEUE_LIMIT] songs, whoever sets
 * them (another controller, say): the Cast framework would throw on the message, and the app with it.
 * A longer queue set while cast goes from its starting song (the one playing, set to carry on from
 * where it is); songs added past the limit are dropped.
 * The app's own controller keeps to it anyway, holding a long queue a window at a time.
 */
@OptIn(UnstableApi::class)
internal class CastQueueCap(player: Player) : ForwardingPlayer(player) {
    private val cast: Boolean get() = deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE

    override fun setMediaItems(mediaItems: List<MediaItem>) = setMediaItems(mediaItems, true)

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        if (!cast || mediaItems.size <= CAST_QUEUE_LIMIT) return super.setMediaItems(mediaItems, resetPosition)
        setMediaItems(mediaItems, if (resetPosition) 0 else C.INDEX_UNSET, C.TIME_UNSET)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        if (!cast || mediaItems.size <= CAST_QUEUE_LIMIT) {
            return super.setMediaItems(
                mediaItems,
                startIndex,
                startPositionMs,
            )
        }
        val (from, positionMs) = if (startIndex == C.INDEX_UNSET) carryingOn(mediaItems) else startIndex to startPositionMs
        super.setMediaItems(mediaItems.castWindowFrom(from.coerceIn(0, mediaItems.size - 1)), 0, positionMs)
    }

    /**
     * Where a queue set to carry on from where playback is (no start given) starts: from the song
     * playing, where it's in [mediaItems], at its position. The cast device holds only a window, so
     * its index says nothing of where that is in a queue set whole; one without it goes from that
     * index, as Media3 does.
     */
    private fun carryingOn(mediaItems: List<MediaItem>): Pair<Int, Long> {
        val playingId = currentMediaItem?.mediaId
        val at = mediaItems.indexOfFirst { it.mediaId == playingId }.takeIf { playingId != null && it >= 0 }
        return (at ?: currentMediaItemIndex) to currentPosition
    }

    override fun addMediaItems(mediaItems: List<MediaItem>) = addMediaItems(mediaItemCount, mediaItems)

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
        val room = if (cast) (CAST_QUEUE_LIMIT - mediaItemCount).coerceAtLeast(0) else mediaItems.size
        if (room > 0) super.addMediaItems(index, mediaItems.take(room))
    }
}
