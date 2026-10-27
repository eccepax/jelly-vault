package org.jellyfin.mobile.downloads

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.common.downloads.DownloadGroups
import org.jellyfin.mobile.common.downloads.ItemDownload
import org.jellyfin.mobile.data.JellyfinDatabase
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.mobile.testing.downloads.FakeItemDownloads
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AudiobookDownloadsTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        JellyfinDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
    private val items = FakeItemDownloads()
    private val chapter1 = UUID.randomUUID().toString()
    private val chapter2 = UUID.randomUUID().toString()
    private var unreachable = setOf<String>()
    private var broken = setOf<String>()

    /** What the server says about a track, kept so it plays with no connection. */
    private fun details(trackId: String): DownloadEntity? {
        if (trackId in unreachable) return null
        check(trackId !in broken) { "Server error for $trackId" }
        val source = LocalJellyfinMediaSource(
            itemId = UUID.fromString(trackId),
            item = null,
            sourceInfo = sourceInfo(id = trackId.replace("-", "")),
            playSessionId = "session",
            localDirectoryUri = "",
            remoteFileUri = "uri($trackId)",
            downloadSize = 0,
        )
        return DownloadEntity(source)
    }

    private fun sourceInfo(id: String) = MediaSourceInfo(
        protocol = MediaProtocol.HTTP, id = id, path = null, encoderPath = null, encoderProtocol = null,
        type = MediaSourceType.DEFAULT, container = "m4b", size = null, name = "Chapter", isRemote = false,
        eTag = null, runTimeTicks = null, readAtNativeFramerate = false, ignoreDts = false, ignoreIndex = false,
        genPtsInput = false, supportsTranscoding = false, supportsDirectStream = false, supportsDirectPlay = true,
        isInfiniteStream = false, useMostCompatibleTranscodingProfile = false, requiresOpening = false,
        openToken = null, requiresClosing = false, liveStreamId = null, bufferMs = null, requiresLooping = false,
        supportsProbing = false, videoType = null, isoType = null, video3dFormat = null, mediaStreams = emptyList(),
        mediaAttachments = emptyList(), formats = emptyList(), bitrate = null, fallbackMaxStreamingBitrate = null,
        timestamp = null, requiredHttpHeaders = emptyMap(), transcodingUrl = null,
        transcodingSubProtocol = MediaStreamProtocol.HTTP, transcodingContainer = null, analyzeDurationMs = null,
        defaultAudioStreamIndex = null, defaultSubtitleStreamIndex = null, hasSegments = false,
    )

    private val downloads = AudiobookDownloads(
        groups = DownloadGroups(items, database.downloadGroupDao, uriOf = { "uri($it)" }),
        dao = database.downloadDao,
        detailsOf = ::details,
    )

    @After
    fun tearDown() = database.close()

    private suspend fun savedDetails() = database.downloadDao.getAllDownloads().first().map { it.itemId }

    @Test
    fun `downloading a book keeps each track's details and fetches its file`() = runTest {
        downloads.download(listOf(chapter1, chapter2))

        items.requests shouldBe listOf("$chapter1 <- uri($chapter1)", "$chapter2 <- uri($chapter2)")
        savedDetails().toSet() shouldBe setOf(chapter1.replace("-", ""), chapter2.replace("-", ""))
    }

    @Test
    fun `a track the server can't describe is skipped, and the rest still download`() = runTest {
        unreachable = setOf(chapter1)

        downloads.download(listOf(chapter1, chapter2))

        items.requests shouldBe listOf("$chapter2 <- uri($chapter2)")
    }

    @Test
    fun `one track failing doesn't stop the rest of the book`() = runTest {
        broken = setOf(chapter1)

        downloads.download(listOf(chapter1, chapter2))

        items.requests shouldBe listOf("$chapter2 <- uri($chapter2)")
    }

    @Test
    fun `a track counts as downloaded only once its file is complete`() = runTest {
        downloads.download(listOf(chapter1, chapter2))
        items.complete(chapter1)

        downloads.downloadedIds.first() shouldBe setOf(chapter1)
    }

    @Test
    fun `a finished track is ready to play from the device, from where it was left`() = runTest {
        downloads.download(listOf(chapter1))
        items.complete(chapter1)
        database.downloadDao.insert(details(chapter1)!!.apply { mediaSource.startTime = 95.seconds })

        val playable = downloads.playable(UUID.fromString(chapter1))

        playable?.itemId shouldBe UUID.fromString(chapter1)
        playable?.startTime shouldBe 95.seconds
    }

    @Test
    fun `a track still downloading isn't ready to play from the device`() = runTest {
        downloads.download(listOf(chapter1))

        downloads.playable(UUID.fromString(chapter1)) shouldBe null
    }

    @Test
    fun `a track that was never downloaded isn't ready to play from the device`() = runTest {
        downloads.playable(UUID.fromString(chapter1)) shouldBe null
    }

    @Test
    fun `the downloads list shows only finished tracks`() = runTest {
        downloads.download(listOf(chapter1, chapter2))
        items.complete(chapter2)

        downloads.finished.first().map { it.mediaSource.itemId.toString() } shouldBe listOf(chapter2)
    }

    @Test
    fun `removing a track deletes its file and its details`() = runTest {
        downloads.download(listOf(chapter1, chapter2))
        items.complete(chapter1, chapter2)

        downloads.remove(listOf(chapter1))

        items.removals shouldBe listOf(chapter1)
        savedDetails() shouldBe listOf(chapter2.replace("-", ""))
        downloads.downloadedIds.first() shouldBe setOf(chapter2)
    }

    @Test
    fun `downloading again retries a track that failed`() = runTest {
        downloads.download(listOf(chapter1))
        items.set(chapter1, ItemDownload.State.FAILED)
        items.requests.clear()

        downloads.download(listOf(chapter1))

        items.requests shouldBe listOf("$chapter1 <- uri($chapter1)")
    }

    @Test
    fun `a track whose file was lost is fetched again at launch`() = runTest {
        downloads.download(listOf(chapter1))
        items.complete(chapter1)
        items.lose(chapter1)
        items.requests.clear()

        downloads.repair()

        items.requests shouldBe listOf("$chapter1 <- uri($chapter1)")
    }

    @Test
    fun `removing everything deletes every track's file and details`() = runTest {
        downloads.download(listOf(chapter1, chapter2))
        items.complete(chapter1, chapter2)

        downloads.removeAll()

        items.removals.toSet() shouldBe setOf(chapter1, chapter2)
        savedDetails().shouldBeEmpty()
        downloads.downloadedIds.first().shouldBeEmpty()
    }

    @Test
    fun `removing everything keeps the listening the server hasn't heard about yet, to send it later`() = runTest {
        downloads.download(listOf(chapter1, chapter2))
        items.complete(chapter1, chapter2)
        listenedOffline(chapter1)

        downloads.removeAll()

        items.removals.toSet() shouldBe setOf(chapter1, chapter2)
        savedDetails() shouldBe listOf(chapter1.replace("-", ""))
        downloads.downloadedIds.first().shouldBeEmpty()
        downloads.finished.first().shouldBeEmpty()
        downloads.isKept(UUID.fromString(chapter1)) shouldBe false
    }

    @Test
    fun `removing a track keeps the listening the server hasn't heard about yet`() = runTest {
        downloads.download(listOf(chapter1))
        items.complete(chapter1)
        listenedOffline(chapter1)

        downloads.remove(listOf(chapter1))

        items.removals shouldBe listOf(chapter1)
        savedDetails() shouldBe listOf(chapter1.replace("-", ""))
        downloads.playable(UUID.fromString(chapter1)) shouldBe null
    }

    @Test
    fun `signing out forgets unsent listening, and a removed track's details with it`() = runTest {
        downloads.download(listOf(chapter1, chapter2))
        items.complete(chapter1, chapter2)
        listenedOffline(chapter1)
        listenedOffline(chapter2)
        downloads.remove(listOf(chapter1))

        downloads.forgetUnsentListening()

        savedDetails() shouldBe listOf(chapter2.replace("-", ""))
        downloads.playable(UUID.fromString(chapter2))?.positionSavedAt shouldBe null
    }

    @Test
    fun `removing everything after forgetting unsent listening leaves nothing`() = runTest {
        downloads.download(listOf(chapter1))
        items.complete(chapter1)
        listenedOffline(chapter1)

        downloads.forgetUnsentListening()
        downloads.removeAll()

        savedDetails().shouldBeEmpty()
    }

    @Test
    fun `downloading a removed track again keeps the listening the server hasn't heard about yet`() = runTest {
        downloads.download(listOf(chapter1))
        items.complete(chapter1)
        database.downloadDao.insert(
            details(chapter1)!!.apply {
                mediaSource.startTime = 2400.seconds
                mediaSource.positionSavedAt = Instant.EPOCH
            },
        )
        downloads.remove(listOf(chapter1))

        downloads.download(listOf(chapter1))

        val kept = database.downloadDao.getAllDownloads().first().single().mediaSource
        kept.startTime shouldBe 2400.seconds
        kept.positionSavedAt shouldBe Instant.EPOCH
    }

    @Test
    fun `removing several tracks at once removes each`() = runTest {
        downloads.download(listOf(chapter1, chapter2))
        items.complete(chapter1, chapter2)

        downloads.remove(listOf(chapter1, chapter2))

        items.removals.toSet() shouldBe setOf(chapter1, chapter2)
        savedDetails().shouldBeEmpty()
    }

    @Test
    fun `a track held back for Wi-Fi is waiting`() = runTest {
        downloads.download(listOf(chapter1, chapter2))
        items.set(chapter1, ItemDownload.State.WAITING)

        downloads.waitingIds.first() shouldBe setOf(chapter1)
    }

    @Test
    fun `a track is kept while it's downloaded or on its way`() = runTest {
        downloads.download(listOf(chapter1))

        downloads.isKept(UUID.fromString(chapter1)) shouldBe true
        downloads.isKept(UUID.fromString(chapter2)) shouldBe false
    }

    /** Played offline: a position the server hasn't been sent (see OfflineListeningSync). */
    private suspend fun listenedOffline(trackId: String) {
        database.downloadDao.insert(details(trackId)!!.apply { mediaSource.positionSavedAt = Instant.EPOCH })
    }

    @Test
    fun `nothing is fetched for an empty book`() = runTest {
        downloads.download(emptyList())

        items.requests.shouldBeEmpty()
    }
}
