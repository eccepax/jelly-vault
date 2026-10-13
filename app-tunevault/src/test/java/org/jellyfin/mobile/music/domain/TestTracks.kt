package org.jellyfin.mobile.music.domain

fun track(id: String) = Track(
    id = id,
    title = "Track $id",
    artist = "Artist",
    albumId = "album",
    albumTitle = "Album",
    durationMs = 180_000L,
)

fun tracks(vararg ids: String) = ids.map(::track)

val PlayQueue.currentId: String? get() = current?.id
val PlayQueue.ids: List<String> get() = tracks.map { it.id }
