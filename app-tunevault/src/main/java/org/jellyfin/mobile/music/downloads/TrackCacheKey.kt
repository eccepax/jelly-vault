package org.jellyfin.mobile.music.downloads

import android.net.Uri
import androidx.media3.datasource.cache.CacheKeyFactory
import java.net.URI

/**
 * Downloads are stored under the song's id, not its URL, so the player finds one whatever quality,
 * server address or token its own stream URL carries.
 */
object TrackCacheKey {
    val factory = CacheKeyFactory { dataSpec ->
        trackIdOf(dataSpec.uri)?.let { "track:$it" } ?: dataSpec.key ?: dataSpec.uri.toString()
    }

    fun trackIdOf(uri: Uri): String? = trackIdOf(uri.toString())

    /** The song id in a Jellyfin `/Audio/{id}/…` URL, or null for anything else. */
    fun trackIdOf(url: String): String? {
        val segments = URI(url).path.orEmpty().split('/').filter { it.isNotEmpty() }
        val audio = segments.indexOfFirst { it.equals("Audio", ignoreCase = true) }
        return segments.getOrNull(audio + 1)?.takeIf { audio >= 0 }
    }
}
