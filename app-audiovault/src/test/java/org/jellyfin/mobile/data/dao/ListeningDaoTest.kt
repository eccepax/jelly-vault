package org.jellyfin.mobile.data.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.data.JellyfinDatabase
import org.jellyfin.mobile.data.entity.FinishedBookEntity
import org.jellyfin.mobile.data.entity.ListeningDayEntity
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Listening kept on the device for the stats: time by day, and the books finished. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ListeningDaoTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        JellyfinDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
    private val dao = database.listeningDao
    private val books = database.finishedBookDao

    @After
    fun tearDown() = database.close()

    @Test
    fun `time listened adds up by day`() = runBlocking<Unit> {
        dao.addListened("2026-11-15", 1_000L)
        dao.addListened("2026-11-15", 2_000L)
        dao.addListened("2026-11-16", 5_000L)

        dao.days().first().sortedBy { it.day } shouldBe
            listOf(ListeningDayEntity("2026-11-15", 3_000L), ListeningDayEntity("2026-11-16", 5_000L))
    }

    @Test
    fun `a book finished again is kept once, as finished last`() = runBlocking<Unit> {
        books.bookFinished(FinishedBookEntity("dune", "Dune", finishedAt = 1L))
        books.bookFinished(FinishedBookEntity("dune", "Dune", finishedAt = 2L))

        books.finishedBooks().first() shouldBe listOf(FinishedBookEntity("dune", "Dune", finishedAt = 2L))
    }

    @Test
    fun `signing out forgets it all`() = runBlocking<Unit> {
        dao.addListened("2026-11-15", 1_000L)
        books.bookFinished(FinishedBookEntity("dune", "Dune", finishedAt = 1L))

        dao.clearDays()
        books.clear()

        dao.days().first() shouldBe emptyList()
        books.finishedBooks().first() shouldBe emptyList()
    }
}
