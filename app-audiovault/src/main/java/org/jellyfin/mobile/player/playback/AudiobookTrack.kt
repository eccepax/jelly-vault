package org.jellyfin.mobile.player.playback

/** Where a chapter starts in its track, and what it's called. */
data class Chapter(val startMs: Long, val name: String?)

/**
 * One file of a book in the play queue: a whole book in one file, or one part of a book split
 * across files. [id] is its Jellyfin item id.
 */
data class AudiobookTrack(
    val id: String,
    val title: String,
    val bookTitle: String? = null,
    val author: String? = null,
    val coverUrl: String? = null,
    /** Its length as the server knows it; 0 when not known. */
    val durationMs: Long,
    val chapters: List<Chapter> = emptyList(),
    /**
     * Played from its download. Where it got to is then kept on the device, and the server hears
     * about it later (see OfflineListeningSync), rather than as it plays.
     */
    val fromDevice: Boolean = false,
    /**
     * The file's container and audio codec, where the server said: what a cast device is sent
     * depends on them (see JellyfinAudiobooks.castStream).
     */
    val container: String? = null,
    val audioCodec: String? = null,
)
