package org.jellyfin.mobile.cast

/**
 * What a cast device is sent for a song: where it fetches it, and the type that url serves. A
 * transcode started part way starts [startMs] into the song, and can't be [seekable]: a seek in it
 * asks for the stream again from there.
 */
data class CastStream(val url: String, val mimeType: String, val startMs: Long = 0L, val seekable: Boolean = true)

/**
 * Where a cast device fetches a song from, found once for each song in the queue (what its file is
 * may take asking the server): its stream from any place in it.
 */
fun interface CastSource {
    fun streamFrom(fromMs: Long): CastStream
}

/**
 * The most songs a cast device is sent at once: the Cast framework refuses a message over 512 KB,
 * and a queue goes in one. An app with longer queues holds them a window at a time while cast.
 */
const val CAST_QUEUE_LIMIT = 50
