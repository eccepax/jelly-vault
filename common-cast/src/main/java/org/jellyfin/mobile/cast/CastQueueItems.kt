package org.jellyfin.mobile.cast

import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import org.jellyfin.mobile.player.session.playableUri
import org.json.JSONObject

/**
 * Items as the Default Media Receiver takes them, and back. Each goes the way Media3's own converter
 * sends it, which also brings it back with its id, uri and metadata: the app's item, its own uri
 * and all, while the cast device fetches the stream the app chose. The entry's key goes in the
 * queue item's custom data, which the cast device sends back. A live item goes as a live stream,
 * so the cast device shows no seek bar or duration.
 */
@UnstableApi
internal object CastQueueItems {

    /** Where the entry's key goes in a queue item's custom data. */
    private const val KEY = "vaultEntry"

    private val converter = DefaultMediaItemConverter()

    fun queueItemOf(sent: SentItem, live: Boolean = false): MediaQueueItem {
        // Media3's converter needs a uri and a type; an item with no uri at all goes with the stream's
        val asGiven = sent.item.buildUpon()
            .setUri(sent.item.playableUri?.toString() ?: sent.stream.url)
            .setMimeType(sent.stream.mimeType)
            .build()
        val converted = converter.toMediaQueueItem(asGiven)
        converted.media?.writer?.apply {
            setContentId(sent.stream.url)
            setContentUrl(sent.stream.url)
            setContentType(sent.stream.mimeType)
            if (live) setStreamType(MediaInfo.STREAM_TYPE_LIVE)
        }
        return MediaQueueItem.Builder(converted)
            .setCustomData(JSONObject().put(KEY, sent.key))
            .setAutoplay(true)
            .build()
    }

    /** The key of the entry it was sent for; null for an item another app sent. */
    fun keyOf(item: MediaQueueItem): String? = item.customData?.optString(KEY)?.takeIf { it.isNotEmpty() }

    /** The app's item as it was sent; null for one that didn't come from Media3's converter. */
    // Another app's item can hold anything: none of it is this app's then
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    fun mediaItemOf(item: MediaQueueItem): MediaItem? = try {
        converter.toMediaItem(item)
    } catch (e: RuntimeException) {
        null
    }
}
