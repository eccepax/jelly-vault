package org.jellyfin.mobile.player.playback

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.player.PlayerException
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ChapterInfo
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaSourceType
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/** What starting a book plays: the library's and downloads' requests, turned into tracks. */
class AudiobookQueueLoaderTest {

    private val partOne = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val partTwo = UUID.fromString("00000000-0000-4000-8000-000000000002")
    private val partThree = UUID.fromString("00000000-0000-4000-8000-000000000003")

    private val server = mutableMapOf<UUID, BaseItemDto>()
    private var serverReachable = true
    private val downloads = mutableMapOf<UUID, LocalJellyfinMediaSource>()

    private val loader = AudiobookQueueLoader(
        items = { ids ->
            if (!serverReachable) throw ApiClientException("No route to host")
            ids.mapNotNull(server::get)
        },
        playable = downloads::get,
        coverUrl = { item -> "https://vault.test/Items/${item.id}/Images/Primary" },
    )

    private fun onServer(id: UUID, name: String, resumeAt: Long = 0L, chapters: List<ChapterInfo>? = null) {
        server[id] = BaseItemDto(
            id = id,
            type = BaseItemKind.AUDIO_BOOK,
            name = name,
            album = "The Book",
            albumArtist = "An Author",
            runTimeTicks = 60.minutes.inWholeMilliseconds * TICKS_PER_MS,
            chapters = chapters,
            userData = userData(resumeAt),
        )
    }

    private fun downloaded(id: UUID, name: String, savedAtMs: Long = 0L, sourceInfo: MediaSourceInfo = sourceInfo()) {
        downloads[id] = LocalJellyfinMediaSource(
            itemId = id,
            item = BaseItemDto(id = id, type = BaseItemKind.AUDIO_BOOK, name = name, runTimeTicks = 600_000_000_000L),
            sourceInfo = sourceInfo,
            playSessionId = "session",
            localDirectoryUri = "/downloads",
            remoteFileUri = "https://vault.test/Items/$id/Download",
            downloadSize = 1L,
        ).apply { startTime = savedAtMs.milliseconds }
    }

    private fun options(
        vararg ids: UUID,
        startIndex: Int = 0,
        startPositionMs: Long? = null,
        fromDownloads: Boolean = false,
        names: List<String> = emptyList(),
    ) = PlayOptions(
        ids = ids.toList(),
        mediaSourceId = null,
        startIndex = startIndex,
        startPosition = startPositionMs?.milliseconds,
        audioStreamIndex = null,
        subtitleStreamIndex = null,
        playFromDownloads = fromDownloads,
        trackNames = names,
    )

    @Test
    fun `a book's parts become tracks, in order, starting where asked`() = runTest {
        onServer(partOne, "Part 1")
        onServer(partTwo, "Part 2")

        val queue = loader.load(options(partOne, partTwo, startIndex = 1, startPositionMs = 5_000L)).getOrThrow()

        queue.tracks.map { it.title } shouldBe listOf("Part 1", "Part 2")
        queue.startIndex shouldBe 1
        queue.startPositionMs shouldBe 5_000L
    }

    @Test
    fun `a track carries what the player shows, its book, author, cover, length and chapters`() = runTest {
        onServer(partOne, "Part 1", chapters = listOf(chapter(0L, "Opening"), chapter(90_000L, "Next")))

        val track = loader.load(options(partOne)).getOrThrow().tracks.single()

        track shouldBe AudiobookTrack(
            id = partOne.toString(),
            title = "Part 1",
            bookTitle = "The Book",
            author = "An Author",
            coverUrl = "https://vault.test/Items/$partOne/Images/Primary",
            durationMs = 60.minutes.inWholeMilliseconds,
            chapters = listOf(Chapter(0L, "Opening"), Chapter(90_000L, "Next")),
            fromDevice = false,
        )
    }

    @Test
    fun `a streamed track says what its file holds, so a cast device can be sent it in a format it plays`() = runTest {
        onServer(partOne, "Part 1")
        server[partOne] = server.getValue(partOne).copy(
            mediaSources = listOf(sourceInfo().copy(container = "m4b", mediaStreams = listOf(audioStream("aac")))),
        )

        val track = loader.load(options(partOne)).getOrThrow().tracks.single()

        track.container shouldBe "m4b"
        track.audioCodec shouldBe "aac"
    }

    @Test
    fun `a downloaded track says what its file holds too`() = runTest {
        downloaded(
            partOne,
            "Part 1",
            sourceInfo = sourceInfo().copy(container = "mp3", mediaStreams = listOf(audioStream("mp3"))),
        )

        val track = loader.load(options(partOne, fromDownloads = true)).getOrThrow().tracks.single()

        track.container shouldBe "mp3"
        track.audioCodec shouldBe "mp3"
    }

    @Test
    fun `without a start position, a streamed book starts where the server says it was left`() = runTest {
        onServer(partOne, "Part 1", resumeAt = 754_000L)

        loader.load(options(partOne)).getOrThrow().startPositionMs shouldBe 754_000L
    }

    @Test
    fun `from the downloads, it plays from the device, where it was left on the device`() = runTest {
        onServer(partOne, "Part 1", resumeAt = 100_000L)
        downloaded(partOne, "Part 1 (downloaded)", savedAtMs = 200_000L)

        val queue = loader.load(options(partOne, fromDownloads = true)).getOrThrow()

        queue.tracks.single().fromDevice shouldBe true
        queue.startPositionMs shouldBe 200_000L
    }

    @Test
    fun `from the library, a downloaded track is still streamed, so the server hears as it plays`() = runTest {
        onServer(partOne, "Part 1")
        downloaded(partOne, "Part 1")

        loader.load(options(partOne)).getOrThrow().tracks.single().fromDevice shouldBe false
    }

    @Test
    fun `with the server out of reach, downloaded parts play from the device`() = runTest {
        downloaded(partOne, "Part 1", savedAtMs = 30_000L)
        downloaded(partTwo, "Part 2")
        serverReachable = false

        val queue = loader.load(options(partOne, partTwo)).getOrThrow()

        queue.tracks.map { it.fromDevice } shouldBe listOf(true, true)
        queue.startPositionMs shouldBe 30_000L
    }

    @Test
    fun `parts that are neither on the server nor downloaded are left out`() = runTest {
        downloaded(partOne, "Part 1")
        downloaded(partThree, "Part 3")
        serverReachable = false

        val queue = loader.load(options(partOne, partTwo, partThree, startIndex = 2)).getOrThrow()

        queue.tracks.map { it.title } shouldBe listOf("Part 1", "Part 3")
        queue.startIndex shouldBe 1
    }

    @Test
    fun `when the part to start can't be had at all, it's a network failure`() = runTest {
        serverReachable = false

        loader.load(options(partOne)).exceptionOrNull().shouldBeInstanceOf<PlayerException.NetworkFailure>()
    }

    @Test
    fun `a request without anything to play is invalid`() = runTest {
        loader.load(options()).exceptionOrNull().shouldBeInstanceOf<PlayerException.InvalidPlayOptions>()
    }

    @Test
    fun `a part without a name on the server takes the name the library showed`() = runTest {
        server[partOne] = BaseItemDto(id = partOne, type = BaseItemKind.AUDIO)

        loader.load(options(partOne, names = listOf("Track 1"))).getOrThrow().tracks.single().title shouldBe "Track 1"
    }

    private fun chapter(startMs: Long, name: String) = ChapterInfo(
        startPositionTicks = startMs * TICKS_PER_MS,
        name = name,
        imageDateModified = LocalDateTime.MIN,
    )

    private fun userData(positionMs: Long) = UserItemDataDto(
        playbackPositionTicks = positionMs * TICKS_PER_MS,
        playCount = 0,
        isFavorite = false,
        played = false,
        key = "",
        itemId = partOne,
    )

    private fun audioStream(codec: String) = MediaStream(
        index = 0,
        type = MediaStreamType.AUDIO,
        codec = codec,
        isExternal = false,
        isTextSubtitleStream = false,
        isInterlaced = false,
        isDefault = true,
        isForced = false,
        isHearingImpaired = false,
        supportsExternalStream = false,
    )

    private fun sourceInfo() = MediaSourceInfo(
        protocol = MediaProtocol.HTTP, id = "src", path = null, encoderPath = null, encoderProtocol = null,
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

    private companion object {
        const val TICKS_PER_MS = 10_000L
    }
}
