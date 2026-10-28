package org.jellyfin.mobile.cast

/**
 * What a cast device is sent for a song: where it fetches it, and the type that url serves. A
 * transcode started part way starts [startMs] into the song, and can't be [seekable]: a seek in it
 * asks for the stream again from there.
 */
data class CastStream(val url: String, val mimeType: String, val startMs: Long = 0L, val seekable: Boolean = true)
