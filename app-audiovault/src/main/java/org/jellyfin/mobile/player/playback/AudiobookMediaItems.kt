package org.jellyfin.mobile.player.playback

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

private const val EXTRA_CHAPTER_STARTS = "org.jellyfin.mobile.chapterStarts"
private const val EXTRA_CHAPTER_NAMES = "org.jellyfin.mobile.chapterNames"
private const val EXTRA_FROM_DEVICE = "org.jellyfin.mobile.fromDevice"
private const val EXTRA_CONTAINER = "org.jellyfin.mobile.container"
private const val EXTRA_AUDIO_CODEC = "org.jellyfin.mobile.audioCodec"

/**
 * The player-side form of a track. Everything the app and the service need travels in the item,
 * so either side can rebuild the track from what the player holds (e.g. after a voice search
 * started playback). The stream url goes in both places: `localConfiguration` for a player used
 * directly, `requestMetadata` because MediaController -> MediaSession strips the former.
 */
fun AudiobookTrack.toMediaItem(streamUrl: (itemId: String) -> String): MediaItem {
    val uri = streamUrl(id).toUri()
    return MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setAlbumTitle(bookTitle)
                .setArtist(author)
                .setArtworkUri(coverUrl?.toUri())
                .setDurationMs(durationMs.takeIf { it > 0 })
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setExtras(
                    Bundle().apply {
                        putLongArray(EXTRA_CHAPTER_STARTS, chapters.map { it.startMs }.toLongArray())
                        putStringArray(EXTRA_CHAPTER_NAMES, chapters.map { it.name.orEmpty() }.toTypedArray())
                        putBoolean(EXTRA_FROM_DEVICE, fromDevice)
                        putString(EXTRA_CONTAINER, container)
                        putString(EXTRA_AUDIO_CODEC, audioCodec)
                    },
                )
                .build(),
        )
        .build()
}

/** The track an item was built from; an item from elsewhere reads as a track without chapters. */
fun MediaItem.toAudiobookTrack(): AudiobookTrack {
    val metadata = mediaMetadata
    val starts = metadata.chapterStartsMs
    val names = metadata.extras?.getStringArray(EXTRA_CHAPTER_NAMES)
    return AudiobookTrack(
        id = mediaId,
        title = metadata.title?.toString().orEmpty(),
        bookTitle = metadata.albumTitle?.toString(),
        author = metadata.artist?.toString(),
        coverUrl = metadata.artworkUri?.toString(),
        durationMs = metadata.durationMs ?: 0L,
        chapters = starts.mapIndexed { i, start -> Chapter(start, names?.getOrNull(i)?.takeIf { it.isNotEmpty() }) },
        fromDevice = metadata.extras?.getBoolean(EXTRA_FROM_DEVICE) == true,
        container = metadata.extras?.getString(EXTRA_CONTAINER),
        audioCodec = metadata.extras?.getString(EXTRA_AUDIO_CODEC),
    )
}

/** Where each of the item's chapters starts, in order; empty when it has none. */
val MediaMetadata.chapterStartsMs: List<Long>
    get() = extras?.getLongArray(EXTRA_CHAPTER_STARTS)?.toList().orEmpty()
