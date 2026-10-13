package org.jellyfin.mobile.music.domain

data class Playlist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val coverUrl: String? = null,
)

/** A song's place in a playlist. [entryId] is the slot, so a song added twice is two entries. */
data class PlaylistEntry(val entryId: String, val track: Track)
