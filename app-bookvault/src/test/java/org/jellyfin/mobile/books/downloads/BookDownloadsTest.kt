package org.jellyfin.mobile.books.downloads

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.books.data.BookDatabase
import org.jellyfin.mobile.common.downloads.DownloadGroups
import org.jellyfin.mobile.common.downloads.GroupStatus
import org.jellyfin.mobile.common.downloads.ItemDownload
import org.jellyfin.mobile.testing.downloads.FakeItemDownloads
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BookDownloadsTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        BookDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
    private val items = FakeItemDownloads()
    private val downloads = BookDownloads(DownloadGroups(items, database.downloadGroupDao, uriOf = { "uri($it)" }))

    @After
    fun tearDown() = database.close()

    @Test
    fun `downloading a book fetches its file`() = runTest {
        downloads.download("b1")

        items.requests shouldBe listOf("b1 <- uri(b1)")
    }

    @Test
    fun `a book counts as downloaded only once its file is complete`() = runTest {
        downloads.download("b1")
        downloads.downloadedIds.first().shouldBeEmpty()
        downloads.status("b1").first() shouldBe GroupStatus.InProgress(done = 0, total = 1)

        items.complete("b1")

        downloads.downloadedIds.first() shouldBe setOf("b1")
        downloads.status("b1").first() shouldBe GroupStatus.Downloaded
    }

    @Test
    fun `removing a download deletes its file`() = runTest {
        downloads.download("b1")
        items.complete("b1")

        downloads.remove("b1")

        items.removals shouldBe listOf("b1")
        downloads.downloadedIds.first().shouldBeEmpty()
    }

    @Test
    fun `removing everything deletes every book's file`() = runTest {
        downloads.download("b1")
        downloads.download("b2")
        items.complete("b1", "b2")

        downloads.removeAll()

        items.removals.toSet() shouldBe setOf("b1", "b2")
        downloads.downloadedIds.first().shouldBeEmpty()
    }

    @Test
    fun `the space used is every book's file together`() = runTest {
        downloads.download("b1")
        downloads.download("b2")
        items.complete("b1", bytes = 2_000L)
        items.complete("b2", bytes = 3_000L)

        downloads.bytesUsed.first() shouldBe 5_000L
    }

    @Test
    fun `a book whose file was lost is fetched again at launch`() = runTest {
        downloads.download("b1")
        items.complete("b1")
        items.lose("b1")
        items.requests.clear()

        downloads.repair()

        items.requests shouldBe listOf("b1 <- uri(b1)")
    }

    @Test
    fun `a failed download can be tried again`() = runTest {
        downloads.download("b1")
        items.set("b1", ItemDownload.State.FAILED)
        items.requests.clear()

        downloads.download("b1")

        items.requests shouldBe listOf("b1 <- uri(b1)")
    }
}
