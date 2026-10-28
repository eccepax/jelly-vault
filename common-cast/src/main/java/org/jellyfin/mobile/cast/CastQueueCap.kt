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
 * A longer queue set while cast goes from its starting song; songs added past the limit are dropped.
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
        val from = startIndex.coerceIn(0, mediaItems.size - 1)
        super.setMediaItems(
            mediaItems.subList(from, minOf(mediaItems.size, from + CAST_QUEUE_LIMIT)),
            0,
            startPositionMs,
        )
    }

    override fun addMediaItems(mediaItems: List<MediaItem>) = addMediaItems(mediaItemCount, mediaItems)

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
        val room = if (cast) (CAST_QUEUE_LIMIT - mediaItemCount).coerceAtLeast(0) else mediaItems.size
        if (room > 0) super.addMediaItems(index, mediaItems.take(room))
    }
}
