package org.jellyfin.mobile.cast

import android.net.Uri
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaQueueItem
import org.jellyfin.mobile.player.session.playableUri

/**
 * Turns the app's items into what the Default Media Receiver plays. The receiver fetches the stream
 * itself, so it needs a url that works on its own: [castUri] makes one (an app whose player sends
 * the token as a header adds it to the url here). The receiver also needs a type, so an item
 * without one is sent as [fallbackMimeType]. An app that knows what each song's url serves says
 * both through [castStream] instead. Everything else goes the way Media3's own converter sends it,
 * which also brings items back with their id, uri and metadata.
 *
 * A [live] converter sends items as live streams, so the cast device shows no seek bar or duration.
 *
 * An item with no way to play it at all (another controller can add one) goes with its id for a
 * uri: the cast device can't play it, but Media3's converter would throw on it in the service.
 */
@UnstableApi
internal class VaultMediaItemConverter(
    private val castUri: (Uri) -> Uri = { it },
    private val fallbackMimeType: String = "audio/mpeg",
    private val castStream: ((MediaItem) -> CastStream?)? = null,
    private val live: Boolean = false,
) : MediaItemConverter {

    private val default = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val stream = castStream?.invoke(mediaItem)
        val forCast = mediaItem.buildUpon()
            .setUri(stream?.url?.let(Uri::parse) ?: mediaItem.playableUri?.let(castUri) ?: unplayable(mediaItem))
            .setMimeType(stream?.mimeType ?: mediaItem.localConfiguration?.mimeType ?: fallbackMimeType)
            .build()
        val sent = default.toMediaQueueItem(forCast)
        if (live) sent.media?.writer?.setStreamType(MediaInfo.STREAM_TYPE_LIVE)
        return sent
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem = default.toMediaItem(mediaQueueItem)

    /** A uri for an item that has none; the cast framework needs one that isn't empty. */
    private fun unplayable(mediaItem: MediaItem): Uri = Uri.parse(mediaItem.mediaId.ifEmpty { "unplayable" })
}

/** What a cast device is sent for a song: where it fetches it, and the type that url serves. */
data class CastStream(val url: String, val mimeType: String)
