package org.jellyfin.mobile.data.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.data.JellyfinDatabase
import org.jellyfin.mobile.data.entity.AudiobookBookmarkEntity
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Bookmarks kept on the device: a place in a track, with an optional note. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AudiobookBookmarkDaoTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        JellyfinDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
    private val dao = database.audiobookBookmarkDao

    @After
    fun tearDown() = database.close()

    private fun mark(track: String, at: Long, savedAt: Long, note: String? = null) =
        AudiobookBookmarkEntity(
            trackId = track,
            positionMs = at,
            label = "$track at $at",
            note = note,
            createdAt = savedAt,
        )

    @Test
    fun `a book's bookmarks are those in its tracks, the newest first`() = runBlocking<Unit> {
        dao.insert(mark("part1", at = 5_000L, savedAt = 1L))
        dao.insert(mark("part2", at = 9_000L, savedAt = 2L))
        dao.insert(mark("another book", at = 1_000L, savedAt = 3L))

        dao.forTracks(listOf("part1", "part2")).first().map { it.label } shouldBe listOf("part2 at 9000", "part1 at 5000")
    }

    @Test
    fun `a bookmark's note can be changed, and the bookmark deleted`() = runBlocking<Unit> {
        dao.insert(mark("part1", at = 5_000L, savedAt = 1L, note = "twist"))
        val id = dao.forTracks(listOf("part1")).first().single().id

        dao.updateNote(id, "big twist")
        dao.forTracks(listOf("part1")).first().single().note shouldBe "big twist"

        dao.delete(id)
        dao.forTracks(listOf("part1")).first() shouldBe emptyList()
    }

    @Test
    fun `signing out forgets every bookmark`() = runBlocking<Unit> {
        dao.insert(mark("part1", at = 5_000L, savedAt = 1L))

        dao.clear()

        dao.forTracks(listOf("part1")).first() shouldBe emptyList()
    }
}
