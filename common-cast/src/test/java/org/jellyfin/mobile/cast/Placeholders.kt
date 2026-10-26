package org.jellyfin.mobile.cast

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** Like Media3's cast player: the queue is there, but each item reads back as MediaItem.EMPTY. */
@OptIn(UnstableApi::class)
class Placeholders(player: Player) : ForwardingPlayer(player) {
    override fun getMediaItemAt(index: Int): MediaItem = MediaItem.EMPTY
    override fun getCurrentMediaItem(): MediaItem? = if (mediaItemCount == 0) null else MediaItem.EMPTY
    override fun getMediaMetadata(): MediaMetadata = MediaMetadata.EMPTY
}
