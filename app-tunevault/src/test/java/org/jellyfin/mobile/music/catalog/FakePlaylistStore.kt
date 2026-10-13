package org.jellyfin.mobile.music.catalog

import org.jellyfin.mobile.music.domain.Playlist
import org.jellyfin.mobile.music.domain.PlaylistEntry
import org.jellyfin.mobile.music.domain.Track

/** An in-memory server: playlists by id, each a list of tracks. */
class FakePlaylistStore(var library: Map<String, Track> = emptyMap()) : PlaylistStore {
    private val names = linkedMapOf<String, String>()
    private val contents = mutableMapOf<String, MutableList<Track>>()
    val writes = mutableListOf<String>()
    var failure: Throwable? = null
    private var nextId = 1

    fun add(id: String, name: String, vararg tracks: Track) {
        names[id] = name
        contents[id] = tracks.toMutableList()
    }

    override suspend fun playlists(): List<Playlist> {
        failure?.let { throw it }
        return names.map { (id, name) -> Playlist(id, name, contents.getValue(id).size) }.sortedBy { it.name }
    }

    override suspend fun playlistEntries(playlistId: String): List<PlaylistEntry> {
        failure?.let { throw it }
        return contents[playlistId].orEmpty().map { PlaylistEntry(entryId = it.id, track = it) }
    }

    override suspend fun createPlaylist(name: String, trackIds: List<String>): String {
        failure?.let { throw it }
        val id = "new${nextId++}"
        writes += "create '$name' with ${trackIds.joinToString(",")}"
        add(id, name, *trackIds.map { library.getValue(it) }.toTypedArray())
        return id
    }

    override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>) {
        failure?.let { throw it }
        writes += "add ${trackIds.joinToString(",")} to $playlistId"
        contents.getValue(playlistId) += trackIds.map { library.getValue(it) }
    }

    override suspend fun removeFromPlaylist(playlistId: String, entryId: String) {
        failure?.let { throw it }
        writes += "remove $entryId from $playlistId"
        contents.getValue(playlistId).removeAll { it.id == entryId }
    }

    override suspend fun deletePlaylist(playlistId: String) {
        names -= playlistId
        contents -= playlistId
    }
}
