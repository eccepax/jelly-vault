package org.jellyfin.mobile.music.playback

import android.net.Uri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.jellyfin.mobile.music.domain.QueueEntry
import org.jellyfin.mobile.music.downloads.TrackCacheKey
import org.jellyfin.mobile.player.session.playableUri

/**
 * The player-side form of a queue slot. The media id is the entry's uid (not the track id) so the
 * player's playlist maps one-to-one onto [org.jellyfin.mobile.music.domain.PlayQueue] even when a
 * track is queued twice. The stream url goes in both places: `localConfiguration` for a player
 * used directly, `requestMetadata` because MediaController -> MediaSession strips the former.
 */
fun QueueEntry.toMediaItem(streamUrl: (trackId: String) -> String): MediaItem {
    val uri = Uri.parse(streamUrl(track.id))
    return MediaItem.Builder()
        .setMediaId(uid.toString())
        .setUri(uri)
        .setRequestMetadata(
            MediaItem.RequestMetadata.Builder()
                .setMediaUri(uri)
                .setExtras(track.container?.let { bundleOf(CONTAINER_EXTRA to it) })
                .build(),
        )
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.albumTitle)
                .setTrackNumber(track.trackNumber)
                .setDiscNumber(track.discNumber)
                .setDurationMs(track.durationMs.takeIf { it > 0 })
                .setArtworkUri(track.coverUrl?.let(Uri::parse))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build(),
        )
        .build()
}

/** The song's container (see [org.jellyfin.mobile.music.domain.Track.container]), kept through a controller. */
private const val CONTAINER_EXTRA = "container"

/** The container of the file this item's song is, or null if the server didn't say. */
val MediaItem.container: String? get() = requestMetadata.extras?.getString(CONTAINER_EXTRA)

/** The queue entry uid this item was built from, or null for a foreign item. */
val MediaItem.queueUid: Long? get() = mediaId.toLongOrNull()

/** The song this item plays, from its stream url (the media id is the queue slot's), or null if it has none. */
val MediaItem.trackId: String?
    get() = playableUri?.let(TrackCacheKey::trackIdOf)
