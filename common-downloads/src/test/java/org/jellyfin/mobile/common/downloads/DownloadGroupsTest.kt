package org.jellyfin.mobile.common.downloads

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.common.downloads.ItemDownload.State
import org.jellyfin.mobile.testing.downloads.FakeItemDownloads
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An app's database, as far as download groups go: each app adds these two tables to its own. */
@Database(entities = [DownloadGroupEntity::class, DownloadGroupItemEntity::class], version = 1, exportSchema = false)
abstract class GroupsTestDatabase : RoomDatabase() {
    abstract val downloadGroupDao: DownloadGroupDao
}

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DownloadGroupsTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        GroupsTestDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
    private val downloads = FakeItemDownloads()
    private var clock = 0L

    private fun groups() = DownloadGroups(
        downloads = downloads,
        dao = database.downloadGroupDao,
        uriOf = { "http://vault/Items/$it/Download" },
        now = { ++clock },
    )

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `saving a group fetches each of its items`() = runTest {
        groups().save("album", listOf("a", "b"))

        downloads.requests shouldBe listOf("a <- http://vault/Items/a/Download", "b <- http://vault/Items/b/Download")
    }

    @Test
    fun `a group counts up as its items finish, then is downloaded`() = runTest {
        val groups = groups()
        groups.save("album", listOf("a", "b"))

        groups.status("album").first() shouldBe GroupStatus.InProgress(done = 0, total = 2)
        downloads.complete("a")
        groups.status("album").first() shouldBe GroupStatus.InProgress(done = 1, total = 2)
        downloads.complete("b")
        groups.status("album").first() shouldBe GroupStatus.Downloaded
    }

    @Test
    fun `something never downloaded says so`() = runTest {
        groups().status("nothing").first() shouldBe GroupStatus.NotDownloaded
    }

    @Test
    fun `saving the same group again fetches nothing new`() = runTest {
        val groups = groups()
        groups.save("album", listOf("a", "b"))
        downloads.complete("a", "b")
        downloads.requests.clear()

        groups.save("album", listOf("a", "b"))

        downloads.requests.shouldBeEmpty()
        groups.status("album").first() shouldBe GroupStatus.Downloaded
    }

    @Test
    fun `an item in two groups is fetched once`() = runTest {
        val groups = groups()
        groups.save("album", listOf("a", "b"))
        groups.save("playlist", listOf("b", "c"))

        downloads.requests.map { it.substringBefore(" ") } shouldBe listOf("a", "b", "c")
    }

    @Test
    fun `removing a group keeps items another group still needs`() = runTest {
        val groups = groups()
        groups.save("album", listOf("a", "b"))
        groups.save("playlist", listOf("b", "c"))
        downloads.complete("a", "b", "c")

        groups.remove("playlist")

        downloads.removals shouldBe listOf("c")
        groups.status("album").first() shouldBe GroupStatus.Downloaded
        groups.status("playlist").first() shouldBe GroupStatus.NotDownloaded
    }

    @Test
    fun `a failed item shows on its group, and retrying fetches only what failed`() = runTest {
        val groups = groups()
        groups.save("album", listOf("a", "b"))
        downloads.complete("a")
        downloads.set("b", State.FAILED)
        downloads.requests.clear()

        groups.status("album").first() shouldBe GroupStatus.Failed(failed = 1, total = 2)
        groups.retry("album")

        downloads.requests.map { it.substringBefore(" ") } shouldBe listOf("b")
    }

    @Test
    fun `items waiting for wi-fi say so`() = runTest {
        val groups = groups()
        groups.save("album", listOf("a", "b"))
        downloads.complete("a")
        downloads.set("b", State.WAITING)

        groups.status("album").first() shouldBe GroupStatus.Waiting(done = 1, total = 2)
    }

    @Test
    fun `an item the downloader lost is fetched again at launch`() = runTest {
        groups().save("album", listOf("a", "b"))
        downloads.complete("a", "b")
        downloads.lose("b")
        downloads.requests.clear()

        groups().repair()

        downloads.requests.map { it.substringBefore(" ") } shouldBe listOf("b")
    }

    @Test
    fun `repair waits until the downloader has loaded what it holds`() = runTest {
        groups().save("album", listOf("a"))
        downloads.complete("a")
        downloads.requests.clear()
        downloads.isReady.value = false
        downloads.lose("a") // not loaded yet, rather than lost

        val repair = launch { groups().repair() }
        testScheduler.runCurrent()
        downloads.requests.shouldBeEmpty()

        downloads.complete("a")
        downloads.isReady.value = true
        repair.join()
        downloads.requests.shouldBeEmpty()
    }

    @Test
    fun `which items are downloaded, for marking rows`() = runTest {
        val groups = groups()
        groups.save("album", listOf("a", "b"))
        downloads.complete("a")

        groups.downloadedItemIds.first() shouldBe setOf("a")
    }

    @Test
    fun `groups are listed newest first, with their items in order`() = runTest {
        val groups = groups()
        groups.save("older", listOf("b", "a"))
        groups.save("newer", listOf("c"))

        groups.groups.first() shouldBe listOf(DownloadGroup("newer", listOf("c")), DownloadGroup("older", listOf("b", "a")))
    }

    @Test
    fun `storage used is the sum of the downloaded items`() = runTest {
        val groups = groups()
        groups.save("album", listOf("a", "b"))
        downloads.complete("a", bytes = 300)
        downloads.complete("b", bytes = 700)

        groups.bytesUsed.first() shouldBe 1_000L
    }

    @Test
    fun `removing everything deletes every item, even ones no group knows`() = runTest {
        val groups = groups()
        groups.save("album", listOf("a"))
        downloads.complete("a", "stray")

        groups.removeAll()

        downloads.removals shouldContainExactlyInAnyOrder listOf("a", "stray")
        groups.groups.first().shouldBeEmpty()
    }

    @Test
    fun `updating a group fetches what was added and deletes what was dropped`() = runTest {
        val groups = groups()
        groups.save("playlist", listOf("a", "b"))
        groups.save("album", listOf("b"))
        downloads.complete("a", "b")
        downloads.requests.clear()

        groups.update("playlist", listOf("b", "c"))

        downloads.requests.map { it.substringBefore(" ") } shouldBe listOf("c")
        downloads.removals shouldBe listOf("a")
        groups.itemIdsOf("playlist") shouldBe listOf("b", "c")
    }

    @Test
    fun `updating a group that isn't downloaded does nothing`() = runTest {
        groups().update("playlist", listOf("a"))

        downloads.requests.shouldBeEmpty()
        groups().contains("playlist") shouldBe false
    }

    @Test
    fun `the same item twice in a group is fetched once`() = runTest {
        groups().save("playlist", listOf("a", "a"))

        downloads.requests.map { it.substringBefore(" ") } shouldBe listOf("a")
    }
}
