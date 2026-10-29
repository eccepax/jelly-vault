package org.jellyfin.mobile.cast

import androidx.annotation.OptIn
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem

/**
 * Items as the cast device gets them: each song's stream as [castStream] says, its details as
 * Media3's own converter has them, with the item's id for the content id. Not the whole item in the
 * custom data, as Media3's converter sends it: the queue goes in one message the Cast framework caps
 * at 512 KB, and the cast player remembers the items it sent anyway. One it didn't send (another
 * sender's) comes back from what the cast device has of it.
 */
@OptIn(UnstableApi::class)
internal class VaultMediaItemConverter(
    private val castStream: (MediaItem) -> CastStream,
    /** Items go as live streams (LiveVault's channels): no end, no seeking. */
    private val live: Boolean = false,
) : MediaItemConverter {
    private val default = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val stream = castStream(mediaItem)
        // Without a type the receiver takes it for a movie, and shows no cover or artist
        val typed = if (mediaItem.mediaMetadata.mediaType != null) {
            mediaItem
        } else {
            mediaItem.buildUpon()
                .setMediaMetadata(
                    mediaItem.mediaMetadata.buildUpon().setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC).build(),
                )
                .build()
        }
        val sent = checkNotNull(default.toMediaQueueItem(typed).media)
        val info = MediaInfo.Builder(mediaItem.mediaId.ifEmpty { stream.url })
            .setContentUrl(stream.url)
            .setContentType(stream.mimeType)
            .setStreamType(if (live) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(sent.metadata)
            .build()
        return MediaQueueItem.Builder(info).build()
    }

    /** One the cast device hasn't sent the details of yet goes by its item id (Media3's converter throws on it). */
    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem =
        if (mediaQueueItem.media == null) {
            MediaItem.Builder().setMediaId("cast:${mediaQueueItem.itemId}").build()
        } else {
            default.toMediaItem(mediaQueueItem)
        }
}
