package org.jellyfin.mobile.music.downloads

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.music.data.TuneDatabase
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Playlist
import org.jellyfin.mobile.music.domain.track
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DownloadRepositoryTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        TuneDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
    private val downloader = FakeTrackDownloader()

    private val album = Album("pb", "The Purple Bird", "Bonnie \"Prince\" Billy", 2025)
    private val a = track("a").copy(albumId = "pb", trackNumber = 1)
    private val b = track("b").copy(albumId = "pb", trackNumber = 2)
    private val other = track("x")
    private val playlist = Playlist("road", "Road trip", 2)

    private fun repository() = DownloadRepository(
        downloader = downloader,
        dao = database.downloadDao,
        downloadUri = { "uri($it)" },
    )

    @After
    fun tearDown() = database.close()

    private suspend fun DownloadRepository.statusOf(id: String) = status(id).first()

    @Test
    fun `downloading an album fetches each of its songs`() = runTest {
        repository().downloadAlbum(album, listOf(a, b))

        downloader.requests shouldBe listOf("a <- uri(a)", "b <- uri(b)")
    }

    @Test
    fun `an album counts up as its songs finish, then is downloaded`() = runTest {
        val repository = repository()
        repository.downloadAlbum(album, listOf(a, b))
        repository.statusOf("pb") shouldBe DownloadStatus.InProgress(done = 0, total = 2)

        downloader.complete("a")
        repository.statusOf("pb") shouldBe DownloadStatus.InProgress(done = 1, total = 2)

        downloader.complete("b")
        repository.statusOf("pb") shouldBe DownloadStatus.Downloaded
    }

    @Test
    fun `something never downloaded says so`() = runTest {
        repository().statusOf("pb") shouldBe DownloadStatus.NotDownloaded
    }

    @Test
    fun `downloading the same album again changes nothing`() = runTest {
        val repository = repository()
        repository.downloadAlbum(album, listOf(a, b))
        downloader.complete("a", "b")

        repository.downloadAlbum(album, listOf(a, b))

        downloader.requests.size shouldBe 2
        repository.statusOf("pb") shouldBe DownloadStatus.Downloaded
    }

    @Test
    fun `a song in an album and a playlist is fetched once`() = runTest {
        val repository = repository()
        repository.downloadAlbum(album, listOf(a, b))
        downloader.complete("a", "b")

        repository.downloadPlaylist(playlist, listOf(b, other))

        downloader.requests shouldBe listOf("a <- uri(a)", "b <- uri(b)", "x <- uri(x)")
        repository.statusOf("road") shouldBe DownloadStatus.InProgress(done = 1, total = 2)
    }

    @Test
    fun `removing a playlist keeps songs a downloaded album still needs`() = runTest {
        val repository = repository()
        repository.downloadAlbum(album, listOf(a, b))
        repository.downloadPlaylist(playlist, listOf(b, other))
        downloader.complete("a", "b", "x")

        repository.remove("road")

        downloader.removals shouldBe listOf("x")
        repository.statusOf("road") shouldBe DownloadStatus.NotDownloaded
        repository.statusOf("pb") shouldBe DownloadStatus.Downloaded
    }

    @Test
    fun `removing the last thing that needs a song deletes it`() = runTest {
        val repository = repository()
        repository.downloadAlbum(album, listOf(a, b))

        repository.remove("pb")

        downloader.removals shouldBe listOf("a", "b")
    }

    @Test
    fun `a failed song shows on the album, and retrying fetches only what failed`() = runTest {
        val repository = repository()
        repository.downloadAlbum(album, listOf(a, b))
        downloader.complete("a")
        downloader.set("b", TrackDownload.State.FAILED)
        repository.statusOf("pb") shouldBe DownloadStatus.Failed(failed = 1, total = 2)

        repository.retry("pb")

        downloader.requests.last() shouldBe "b <- uri(b)"
        downloader.requests.size shouldBe 3
    }

    @Test
    fun `songs waiting for wi-fi say so`() = runTest {
        val repository = repository()
        repository.downloadAlbum(album, listOf(a, b))
        downloader.set("a", TrackDownload.State.WAITING)

        repository.statusOf("pb") shouldBe DownloadStatus.Waiting(done = 0, total = 2)
    }

    @Test
    fun `downloads are remembered across launches`() = runTest {
        repository().downloadAlbum(album, listOf(a, b))
        downloader.complete("a", "b")

        repository().statusOf("pb") shouldBe DownloadStatus.Downloaded
    }

    @Test
    fun `a song the downloader lost is fetched again at launch`() = runTest {
        repository().downloadAlbum(album, listOf(a, b))
        downloader.complete("a", "b")
        downloader.lose("b")

        repository().repair()

        downloader.requests.last() shouldBe "b <- uri(b)"
    }

    @Test
    fun `which songs are downloaded, for marking rows`() = runTest {
        val repository = repository()
        repository.downloadAlbum(album, listOf(a, b))
        downloader.complete("a")

        repository.downloadedTrackIds.first() shouldBe setOf("a")
    }

    @Test
    fun `downloaded albums and playlists are listed, newest first, with their songs in order`() = runTest {
        val repository = repository()
        repository.downloadAlbum(album, listOf(a, b))
        repository.downloadPlaylist(playlist, listOf(other, a))

        val collections = repository.collections.first()

        collections.map { it.id } shouldBe listOf("road", "pb")
        collections.first().tracks.map { it.id } shouldBe listOf("x", "a")
        collections.last().kind shouldBe DownloadedCollection.Kind.ALBUM
        collections.last().title shouldBe "The Purple Bird"
    }

    @Test
    fun `storage used is the sum of the downloaded songs`() = runTest {
        val repository = repository()
        repository.downloadAlbum(album, listOf(a, b))
        downloader.complete("a", bytes = 3_000L)
        downloader.complete("b", bytes = 4_000L)

        repository.bytesUsed.first() shouldBe 7_000L
    }

    @Test
    fun `removing everything deletes every song`() = runTest {
        val repository = repository()
        repository.downloadAlbum(album, listOf(a, b))
        repository.downloadPlaylist(playlist, listOf(other))

        repository.removeAll()

        downloader.removals.toSet() shouldBe setOf("a", "b", "x")
        repository.collections.first().shouldBeEmpty()
    }

    @Test
    fun `a downloaded playlist follows the server's changes`() = runTest {
        val repository = repository()
        repository.downloadPlaylist(playlist, listOf(a, b))
        downloader.complete("a", "b")

        repository.updatePlaylist(playlist, listOf(b, other))

        downloader.requests.last() shouldBe "x <- uri(x)"
        downloader.removals shouldBe listOf("a")
        repository.collections.first().single().tracks.map { it.id } shouldBe listOf("b", "x")
    }

    @Test
    fun `updating a playlist that isn't downloaded does nothing`() = runTest {
        repository().updatePlaylist(playlist, listOf(a))

        downloader.requests.shouldBeEmpty()
    }

    @Test
    fun `repair waits until the downloader has loaded what it holds`() = runTest {
        repository().downloadAlbum(album, listOf(a, b))
        downloader.complete("a", "b")
        val requestsBefore = downloader.requests.size
        // A fresh launch: the downloader is still reading its index, so it looks empty.
        val held = downloader.downloads.value
        downloader.downloads.value = emptyMap()
        downloader.isReady.value = false

        val repair = launch { repository().repair() }
        testScheduler.runCurrent()
        downloader.requests.size shouldBe requestsBefore

        downloader.downloads.value = held
        downloader.isReady.value = true
        repair.join()

        downloader.requests.size shouldBe requestsBefore
    }

    @Test
    fun `a downloaded song keeps its loudness gain, for levelling offline`() = runTest {
        val repository = repository()

        repository.downloadAlbum(album, listOf(a.copy(normalizationGainDb = -7.5f)))

        repository.collections.first().single().tracks.single().normalizationGainDb shouldBe -7.5f
    }
}
