package org.jellyfin.mobile.player

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.jellyfin.mobile.data.dao.AudiobookBookmarkDao
import org.jellyfin.mobile.data.entity.AudiobookBookmarkEntity

/** Bookmarks kept in memory, ids from 1, as the database would keep them. */
class FakeBookmarks : AudiobookBookmarkDao {
    val saved = MutableStateFlow<List<AudiobookBookmarkEntity>>(emptyList())

    override suspend fun insert(bookmark: AudiobookBookmarkEntity) {
        saved.update { it + bookmark.copy(id = (it.maxOfOrNull { b -> b.id } ?: 0L) + 1) }
    }

    override suspend fun delete(id: Long) = saved.update { list -> list.filterNot { it.id == id } }

    override suspend fun updateNote(id: Long, note: String?) =
        saved.update { list -> list.map { if (it.id == id) it.copy(note = note) else it } }

    override fun forTracks(trackIds: List<String>): Flow<List<AudiobookBookmarkEntity>> =
        saved.map { list -> list.filter { it.trackId in trackIds }.sortedByDescending { it.createdAt } }

    override suspend fun clear() = saved.update { emptyList() }
}
