package org.jellyfin.mobile.music.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.jellyfin.mobile.music.domain.QueueEntry

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
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.albumTitle)
                .setTrackNumber(track.trackNumber)
                .setDiscNumber(track.discNumber)
                .setArtworkUri(track.coverUrl?.let(Uri::parse))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build(),
        )
        .build()
}

/** The queue entry uid this item was built from, or null for a foreign item. */
val MediaItem.queueUid: Long? get() = mediaId.toLongOrNull()

/** Session side: restores the stream uri from requestMetadata if the controller stripped it. */
fun MediaItem.withPlayableUri(): MediaItem {
    if (localConfiguration != null) return this
    val uri = requestMetadata.mediaUri ?: return this
    return buildUpon().setUri(uri).build()
}
