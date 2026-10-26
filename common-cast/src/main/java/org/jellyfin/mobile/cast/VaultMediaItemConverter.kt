package org.jellyfin.mobile.cast

import android.net.Uri
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.MediaQueueItem
import org.jellyfin.mobile.player.session.playableUri

/**
 * Turns the app's items into what the Default Media Receiver plays. The receiver fetches the stream
 * itself, so it needs a url that works on its own: [castUri] makes one (an app whose player sends
 * the token as a header adds it to the url here). The receiver also needs a type, so an item
 * without one is sent as [fallbackMimeType]. Everything else goes the way Media3's own converter
 * sends it, which also brings items back with their id, uri and metadata.
 */
@UnstableApi
internal class VaultMediaItemConverter(
    private val castUri: (Uri) -> Uri = { it },
    private val fallbackMimeType: String = "audio/mpeg",
) : MediaItemConverter {

    private val default = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val uri = mediaItem.playableUri
        val mimeType = mediaItem.localConfiguration?.mimeType ?: fallbackMimeType
        val forCast = mediaItem.buildUpon()
            .setUri(uri?.let(castUri))
            .setMimeType(mimeType)
            .build()
        return default.toMediaQueueItem(forCast)
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem = default.toMediaItem(mediaQueueItem)
}
