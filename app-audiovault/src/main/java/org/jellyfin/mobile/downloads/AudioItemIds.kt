package org.jellyfin.mobile.downloads

import android.net.Uri
import org.jellyfin.sdk.model.serializer.toUUIDOrNull

/**
 * The track a URL fetches, if it's a track's audio: `/Audio/{id}/…` streams or an
 * `/Items/{id}/Download` file. Downloads are kept by track id (in its dashed form), so the player
 * finds one whatever quality or token its own URL carries. Artwork and anything else gives null.
 */
fun audioItemIdOf(uri: Uri): String? {
    val segments = uri.pathSegments
    val audio = segments.indexOfFirst { it.equals("Audio", ignoreCase = true) }
    val items = segments.indexOfFirst { it.equals("Items", ignoreCase = true) }
    val isDownload = items >= 0 && segments.getOrNull(items + 2).equals("Download", ignoreCase = true)
    val id = when {
        audio >= 0 -> segments.getOrNull(audio + 1)
        isDownload -> segments.getOrNull(items + 1)
        else -> null
    }
    return id?.toUUIDOrNull()?.toString()
}
