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
import java.util.UUID

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
    fun `the downloads list shows only finished tracks`() = runTest {
        downloads.download(listOf(chapter1, chapter2))
        items.complete(chapter2)

        downloads.finished.first().map { it.mediaSource.itemId.toString() } shouldBe listOf(chapter2)
    }

    @Test
    fun `removing a track deletes its file and its details`() = runTest {
        downloads.download(listOf(chapter1, chapter2))
        items.complete(chapter1, chapter2)

        downloads.remove(chapter1)

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
    fun `nothing is fetched for an empty book`() = runTest {
        downloads.download(emptyList())

        items.requests.shouldBeEmpty()
    }
}
