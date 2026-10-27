package org.jellyfin.mobile.music.domain

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val albumId: String,
    val albumTitle: String,
    val durationMs: Long,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val coverUrl: String? = null,
    val isFavorite: Boolean = false,
    /** The server's measured adjustment to a common loudness, in dB; null if not measured. */
    val normalizationGainDb: Float? = null,
    /** The artist whose page this track leads to: its album's artist, else its own; null if none is known. */
    val artistId: String? = null,
    /** The file's container as the server names it (flac, mp3, m4a, ...); null if it didn't say. */
    val container: String? = null,
)
