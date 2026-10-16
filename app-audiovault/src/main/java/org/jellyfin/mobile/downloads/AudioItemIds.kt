package org.jellyfin.mobile.downloads

import android.net.Uri
import org.jellyfin.sdk.model.serializer.toUUIDOrNull

/**
 * The track a URL fetches, if it's a track's audio: `/Audio/{id}/…` streams or an
 * `/Items/{id}/Download` file, or the track's file as the player streams it (`/Videos/{id}/stream…`,
 * whatever the item type). Downloads are kept by track id (in its dashed form), so the player
 * finds one whatever quality or token its own URL carries. Artwork and anything else gives null.
 */
fun audioItemIdOf(uri: Uri): String? {
    val segments = uri.pathSegments
    val audio = segments.indexOfFirst { it.equals("Audio", ignoreCase = true) }
    val items = segments.indexOfFirst { it.equals("Items", ignoreCase = true) }
    val videos = segments.indexOfFirst { it.equals("Videos", ignoreCase = true) }
    // The player's direct play / direct stream: /Videos/{id}/stream or /Videos/{id}/stream.{container}
    val isFileStream = videos >= 0 && segments.getOrNull(videos + 2)?.startsWith("stream", ignoreCase = true) == true
    val isDownload = items >= 0 && segments.getOrNull(items + 2).equals("Download", ignoreCase = true)
    val id = when {
        audio >= 0 -> segments.getOrNull(audio + 1)
        isDownload -> segments.getOrNull(items + 1)
        isFileStream -> segments.getOrNull(videos + 1)
        else -> null
    }
    return id?.toUUIDOrNull()?.toString()
}
