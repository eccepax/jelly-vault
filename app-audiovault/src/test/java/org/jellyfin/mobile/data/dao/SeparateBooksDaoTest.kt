package org.jellyfin.mobile.data.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.data.JellyfinDatabase
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The books of several files the listener asked to see as separate books, kept on the device. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SeparateBooksDaoTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        JellyfinDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
    private val dao = database.audiobookEntryDao

    @After
    fun tearDown() = database.close()

    @Test
    fun `a book asked to be separate books is remembered, until asked to be one again`() = runBlocking<Unit> {
        dao.separateBooks().first() shouldBe emptyList()

        dao.showAsSeparateBooks("B:/books/Rowling:harry potter")
        dao.showAsSeparateBooks("B:/books/Rowling:harry potter")
        dao.separateBooks().first() shouldBe listOf("B:/books/Rowling:harry potter")

        dao.showAsOneBook("B:/books/Rowling:harry potter")
        dao.separateBooks().first() shouldBe emptyList()
    }
}
