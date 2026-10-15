package org.jellyfin.mobile.downloads

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.data.JellyfinDatabase
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.TimeoutException
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class OfflineListeningSyncTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        JellyfinDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
    private val server = FakeServerPositions()
    private val sync = OfflineListeningSync(database.downloadDao, server)

    private val morning = Instant.parse("2026-10-15T08:00:00Z")
    private val noon = Instant.parse("2026-10-15T12:00:00Z")

    @After
    fun tearDown() = database.close()

    @Test
    fun `listening the server hasn't heard about is sent, as of when it happened`() = runTest {
        val chapter = saved(position = 12.minutes, savedAt = noon)

        sync.sync()

        server.recorded shouldBe listOf(Recorded(chapter, 12.minutes, finished = false, at = noon))
    }

    @Test
    fun `a finished track is sent as played`() = runTest {
        val chapter = saved(position = Duration.ZERO, savedAt = noon, finished = true)

        sync.sync()

        server.recorded shouldBe listOf(Recorded(chapter, Duration.ZERO, finished = true, at = noon))
    }

    @Test
    fun `once sent, it isn't sent again`() = runTest {
        saved(position = 12.minutes, savedAt = noon)

        sync.sync()
        sync.sync()

        server.recorded.size shouldBe 1
        database.downloadDao.getAllDownloads().first().single().mediaSource.positionSavedAt shouldBe null
    }

    @Test
    fun `listening on another device since then isn't overwritten`() = runTest {
        val chapter = saved(position = 12.minutes, savedAt = morning)
        server.lastPlayed[chapter] = noon

        sync.sync()

        server.recorded.shouldBeEmpty()
        database.downloadDao.getAllDownloads().first().single().mediaSource.positionSavedAt shouldBe null
    }

    @Test
    fun `older listening elsewhere is overwritten`() = runTest {
        val chapter = saved(position = 12.minutes, savedAt = noon)
        server.lastPlayed[chapter] = morning

        sync.sync()

        server.recorded.map { it.itemId } shouldBe listOf(chapter)
    }

    @Test
    fun `tracks with nothing waiting aren't sent`() = runTest {
        saved(position = 12.minutes, savedAt = null)

        sync.sync()

        server.recorded.shouldBeEmpty()
        server.asked.shouldBeEmpty()
    }

    @Test
    fun `with no connection, it waits for the next time`() = runTest {
        saved(position = 12.minutes, savedAt = noon)
        server.unreachable = true

        sync.sync()

        database.downloadDao.getAllDownloads().first().single().mediaSource.positionSavedAt shouldBe noon
    }

    @Test
    fun `a server that refuses it isn't asked again`() = runTest {
        saved(position = 12.minutes, savedAt = noon)
        server.refuses = true

        sync.sync()

        database.downloadDao.getAllDownloads().first().single().mediaSource.positionSavedAt shouldBe null
    }

    @Test
    fun `a position saved while it was sending is kept, to send next time`() = runTest {
        val chapter = saved(position = 12.minutes, savedAt = morning)
        // The player saves again while the server is being asked.
        server.onAsk = { saved(position = 13.minutes, savedAt = noon, itemId = chapter) }

        sync.sync()

        val stored = database.downloadDao.getAllDownloads().first().single().mediaSource
        stored.startTime shouldBe 13.minutes
        stored.positionSavedAt shouldBe noon
    }

    private suspend fun saved(
        position: Duration,
        savedAt: Instant?,
        finished: Boolean = false,
        itemId: UUID = UUID.randomUUID(),
    ): UUID {
        val source = LocalJellyfinMediaSource(
            itemId = itemId,
            item = null,
            sourceInfo = sourceInfo(itemId.toString().replace("-", "")),
            playSessionId = "session",
            localDirectoryUri = "",
            remoteFileUri = "",
            downloadSize = 0,
        ).apply {
            startTime = position
            positionSavedAt = savedAt
            this.finished = finished
        }
        database.downloadDao.insert(DownloadEntity(source))
        return itemId
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

    private data class Recorded(val itemId: UUID, val position: Duration, val finished: Boolean, val at: Instant)

    private class FakeServerPositions : ServerPositions {
        val lastPlayed = mutableMapOf<UUID, Instant>()
        val recorded = mutableListOf<Recorded>()
        val asked = mutableListOf<UUID>()
        var unreachable = false
        var refuses = false
        var onAsk: suspend () -> Unit = {}

        override suspend fun lastPlayed(itemId: UUID): Instant? {
            if (unreachable) throw TimeoutException("Vault unreachable")
            asked += itemId
            onAsk()
            return lastPlayed[itemId]
        }

        override suspend fun record(itemId: UUID, position: Duration, finished: Boolean, at: Instant) {
            if (refuses) throw InvalidStatusException(400)
            recorded += Recorded(itemId, position, finished, at)
        }
    }
}
