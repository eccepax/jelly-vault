package org.jellyfin.mobile.player.queue

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.downloads.AudiobookDownloads
import org.jellyfin.mobile.player.PlayerException
import org.jellyfin.mobile.player.PlayerHost
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.player.source.RemoteJellyfinMediaSource
import org.jellyfin.mobile.player.ui.PlayState
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.api.operations.VideosApi
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlayMethod
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tests [QueueManager], including that a resolver error is propagated rather than swallowed as
 * `null`. Queue state is set via reflection for the navigation tests, as there's no production seam.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class QueueManagerTest {

    private val apiClient = mockk<ApiClient>(relaxed = true)
    private val videosApi = mockk<VideosApi>(relaxed = true)
    private val mediaSourceResolver = mockk<MediaSourceResolver>(relaxed = true)
    private val deviceProfileBuilder = mockk<DeviceProfileBuilder>(relaxed = true)
    private val downloadDao = mockk<DownloadDao>(relaxed = true)
    private val downloads = mockk<AudiobookDownloads> { coEvery { playable(any()) } returns null }
    private val progressiveFactory = mockk<ProgressiveMediaSource.Factory>(relaxed = true)
    private val hlsFactory = mockk<HlsMediaSource.Factory>(relaxed = true)
    private val subtitleFactory = mockk<SingleSampleMediaSource.Factory>(relaxed = true)

    private val viewModel = mockk<PlayerHost>(relaxed = true)
    private lateinit var queueManager: QueueManager

    @Before
    fun setUp() {
        // apiClient.videosApi is an extension property with real logic that breaks when called on
        // a plain mock (see ApiClientControllerTest) — stub it directly.
        mockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
        every { apiClient.videosApi } returns videosApi

        startKoin {
            modules(
                module {
                    single { apiClient }
                    single { mediaSourceResolver }
                    single { deviceProfileBuilder }
                    single { downloadDao }
                    single { downloads }
                    single { progressiveFactory }
                    single { hlsFactory }
                    single { subtitleFactory }
                },
            )
        }

        queueManager = QueueManager(viewModel)
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
    }

    private fun setField(name: String, value: Any?) {
        val field = QueueManager::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(queueManager, value)
    }

    private fun setQueue(ids: List<UUID>, index: Int) {
        setField("currentQueue", ids)
        setField("currentQueueIndex", index)
    }

    private fun setCurrentMediaSource(source: JellyfinMediaSource?) {
        val field = QueueManager::class.java.getDeclaredField("_currentMediaSource")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(queueManager) as MutableLiveData<JellyfinMediaSource>).value = source
    }

    /** A [RemoteJellyfinMediaSource] that resolves through the simplest (DIRECT_STREAM) media-source-building path. */
    private fun remoteSource(itemId: UUID = UUID.randomUUID(), maxBitrate: Int? = null): RemoteJellyfinMediaSource {
        val sourceInfo = mockk<MediaSourceInfo>(relaxed = true)
        every { sourceInfo.container } returns "mp4"
        val source = mockk<RemoteJellyfinMediaSource>(relaxed = true)
        every { source.itemId } returns itemId
        every { source.id } returns itemId.toString()
        every { source.playMethod } returns PlayMethod.DIRECT_STREAM
        every { source.sourceInfo } returns sourceInfo
        every { source.maxStreamingBitrate } returns maxBitrate
        every { source.externalSubtitleStreams } returns emptyList()
        every { source.selectedAudioStreamIndex } returns null
        every { source.selectedSubtitleStreamIndex } returns -1
        return source
    }

    /** A track whose file is on the device, ready to play. */
    private fun downloaded(itemId: UUID): LocalJellyfinMediaSource {
        val source = mockk<LocalJellyfinMediaSource>(relaxed = true)
        every { source.itemId } returns itemId
        every { source.externalSubtitleStreams } returns emptyList()
        coEvery { downloads.playable(itemId) } returns source
        return source
    }

    private fun stubFailedResolve(error: PlayerException = PlayerException.NetworkFailure()) {
        coEvery {
            mediaSourceResolver.resolveMediaSource(
                itemId = any(),
                mediaSourceId = any(),
                deviceProfile = any(),
                maxStreamingBitrate = any(),
                startTime = any(),
                audioStreamIndex = any(),
                subtitleStreamIndex = any(),
            )
        } returns Result.failure(error)
    }

    private fun fromDownloads(ids: List<UUID>, startIndex: Int) = PlayOptions(
        ids = ids,
        mediaSourceId = null,
        startIndex = startIndex,
        startPosition = null,
        audioStreamIndex = null,
        subtitleStreamIndex = null,
        playFromDownloads = true,
    )

    private fun stubSuccessfulResolve(result: RemoteJellyfinMediaSource) {
        coEvery {
            mediaSourceResolver.resolveMediaSource(
                itemId = any(),
                mediaSourceId = any(),
                deviceProfile = any(),
                maxStreamingBitrate = any(),
                startTime = any(),
                audioStreamIndex = any(),
                subtitleStreamIndex = any(),
            )
        } returns Result.success(result)
    }

    // --- initializePlaybackQueue -------------------------------------------------------------------

    @Test
    fun `returns InvalidPlayOptions when there is nothing to play`() = runTest {
        val error = queueManager.initializePlaybackQueue(
            PlayOptions(
                ids = emptyList(),
                mediaSourceId = null,
                startIndex = 0,
                startPosition = null,
                audioStreamIndex = null,
                subtitleStreamIndex = null,
                playFromDownloads = null,
            ),
        )

        error should beInstanceOf<PlayerException.InvalidPlayOptions>()
    }

    @Test
    fun `starts remote playback and returns null on success`() = runTest {
        val itemId = UUID.randomUUID()
        stubSuccessfulResolve(remoteSource(itemId))

        val error = queueManager.initializePlaybackQueue(
            PlayOptions(
                ids = listOf(itemId),
                mediaSourceId = null,
                startIndex = 0,
                startPosition = null,
                audioStreamIndex = null,
                subtitleStreamIndex = null,
                playFromDownloads = null,
            ),
        )

        error shouldBe null
        queueManager.queueSize shouldBe 1
        queueManager.currentIndex shouldBe 0
        coVerify {
            mediaSourceResolver.resolveMediaSource(
                itemId = itemId,
                mediaSourceId = null,
                deviceProfile = any(),
                maxStreamingBitrate = null,
                startTime = null,
                audioStreamIndex = null,
                subtitleStreamIndex = null,
            )
        }
        verify { viewModel.load(any(), any(), true) }
    }

    /** Regression coverage: this used to always return null instead of the resolver's real error. */
    @Test
    fun `propagates the resolver's error instead of swallowing it`() = runTest {
        coEvery {
            mediaSourceResolver.resolveMediaSource(
                itemId = any(),
                mediaSourceId = any(),
                deviceProfile = any(),
                maxStreamingBitrate = any(),
                startTime = any(),
                audioStreamIndex = any(),
                subtitleStreamIndex = any(),
            )
        } returns Result.failure(PlayerException.NetworkFailure())

        val error = queueManager.initializePlaybackQueue(
            PlayOptions(
                ids = listOf(UUID.randomUUID()),
                mediaSourceId = null,
                startIndex = 0,
                startPosition = null,
                audioStreamIndex = null,
                subtitleStreamIndex = null,
                playFromDownloads = null,
            ),
        )

        error should beInstanceOf<PlayerException.NetworkFailure>()
        verify(exactly = 0) { viewModel.load(any(), any(), any()) }
    }

    // --- Downloads ---------------------------------------------------------------------------------

    @Test
    fun `playing from downloads plays the chosen track from the device`() = runTest {
        val ids = List(3) { UUID.randomUUID() }
        val chapter2 = downloaded(ids[1])

        val error = queueManager.initializePlaybackQueue(fromDownloads(ids, startIndex = 1))

        error shouldBe null
        queueManager.currentIndex shouldBe 1
        verify { viewModel.load(chapter2, any(), true) }
        coVerify(
            exactly = 0,
        ) { mediaSourceResolver.resolveMediaSource(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `playing from downloads streams a track whose download is gone`() = runTest {
        val id = UUID.randomUUID()
        val streamed = remoteSource(id)
        stubSuccessfulResolve(streamed)

        val error = queueManager.initializePlaybackQueue(fromDownloads(listOf(id), startIndex = 0))

        error shouldBe null
        verify { viewModel.load(streamed, any(), true) }
    }

    @Test
    fun `a download that's gone, with no connection, is an error rather than nothing happening`() = runTest {
        stubFailedResolve(PlayerException.NetworkFailure())

        val error = queueManager.initializePlaybackQueue(fromDownloads(listOf(UUID.randomUUID()), startIndex = 0))

        error should beInstanceOf<PlayerException.NetworkFailure>()
    }

    @Test
    fun `while playing from the device, next plays the next downloaded track`() = runTest {
        val ids = List(3) { UUID.randomUUID() }
        setQueue(ids, index = 0)
        setCurrentMediaSource(downloaded(ids[0]))
        val chapter2 = downloaded(ids[1])

        queueManager.next() shouldBe true

        queueManager.currentIndex shouldBe 1
        verify { viewModel.load(chapter2, any(), true) }
        coVerify(
            exactly = 0,
        ) { mediaSourceResolver.resolveMediaSource(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `while playing from the device, previous plays the previous downloaded track`() = runTest {
        val ids = List(3) { UUID.randomUUID() }
        setQueue(ids, index = 2)
        setCurrentMediaSource(downloaded(ids[2]))
        val chapter2 = downloaded(ids[1])

        queueManager.previous() shouldBe true

        queueManager.currentIndex shouldBe 1
        verify { viewModel.load(chapter2, any(), true) }
    }

    @Test
    fun `while playing from the device, a next track that isn't downloaded is streamed`() = runTest {
        val ids = List(2) { UUID.randomUUID() }
        setQueue(ids, index = 0)
        setCurrentMediaSource(downloaded(ids[0]))
        val streamed = remoteSource(ids[1])
        stubSuccessfulResolve(streamed)

        queueManager.next() shouldBe true

        verify { viewModel.load(streamed, any(), true) }
    }

    @Test
    fun `when the next track can't be streamed, it plays from its download`() = runTest {
        // The connection dropped partway through a downloaded book that was being streamed.
        val ids = List(2) { UUID.randomUUID() }
        setQueue(ids, index = 0)
        setCurrentMediaSource(remoteSource(ids[0]))
        stubFailedResolve(PlayerException.NetworkFailure())
        val chapter2 = downloaded(ids[1])

        queueManager.next() shouldBe true

        queueManager.currentIndex shouldBe 1
        verify { viewModel.load(chapter2, any(), true) }
    }

    @Test
    fun `next reports failure when the next track can't be streamed and isn't downloaded`() = runTest {
        val ids = List(2) { UUID.randomUUID() }
        setQueue(ids, index = 0)
        setCurrentMediaSource(remoteSource(ids[0]))
        stubFailedResolve(PlayerException.NetworkFailure())

        queueManager.next() shouldBe false
    }

    // --- hasPrevious / hasNext ----------------------------------------------------------------------

    @Test
    fun `hasPrevious and hasNext reflect the current position in the queue`() {
        val ids = List(3) { UUID.randomUUID() }

        setQueue(ids, index = 0)
        queueManager.hasPrevious() shouldBe false
        queueManager.hasNext() shouldBe true

        setQueue(ids, index = 1)
        queueManager.hasPrevious() shouldBe true
        queueManager.hasNext() shouldBe true

        setQueue(ids, index = 2)
        queueManager.hasPrevious() shouldBe true
        queueManager.hasNext() shouldBe false
    }

    @Test
    fun `hasPrevious and hasNext are both false for an empty queue`() {
        setQueue(emptyList(), index = 0)

        queueManager.hasPrevious() shouldBe false
        queueManager.hasNext() shouldBe false
    }

    // --- previous ---------------------------------------------------------------------------------

    @Test
    fun `previous does nothing at the start of the queue`() = runTest {
        val ids = List(2) { UUID.randomUUID() }
        setQueue(ids, index = 0)

        queueManager.previous() shouldBe false
        queueManager.currentIndex shouldBe 0
    }

    @Test
    fun `previous moves to the prior track and resolves it`() = runTest {
        val ids = List(3) { UUID.randomUUID() }
        setQueue(ids, index = 2)
        setCurrentMediaSource(remoteSource(ids[2], maxBitrate = 4_000_000))
        stubSuccessfulResolve(remoteSource(ids[1]))

        queueManager.previous() shouldBe true

        queueManager.currentIndex shouldBe 1
        coVerify {
            mediaSourceResolver.resolveMediaSource(
                itemId = ids[1],
                mediaSourceId = null,
                deviceProfile = any(),
                maxStreamingBitrate = 4_000_000,
                startTime = null,
                audioStreamIndex = null,
                subtitleStreamIndex = null,
            )
        }
    }

    // --- next -------------------------------------------------------------------------------------

    @Test
    fun `next does nothing at the end of the queue`() = runTest {
        val ids = List(2) { UUID.randomUUID() }
        setQueue(ids, index = 1)

        queueManager.next() shouldBe false
        queueManager.currentIndex shouldBe 1
    }

    @Test
    fun `next returns false with no current source even though more tracks remain`() = runTest {
        val ids = List(2) { UUID.randomUUID() }
        setQueue(ids, index = 0)
        setCurrentMediaSource(null)

        queueManager.next() shouldBe false
        queueManager.currentIndex shouldBe 0
    }

    @Test
    fun `next continues remote playback for a remote current source`() = runTest {
        val ids = List(3) { UUID.randomUUID() }
        setQueue(ids, index = 0)
        setCurrentMediaSource(remoteSource(ids[0], maxBitrate = 2_000_000))
        stubSuccessfulResolve(remoteSource(ids[1]))

        queueManager.next() shouldBe true

        queueManager.currentIndex shouldBe 1
        coVerify {
            mediaSourceResolver.resolveMediaSource(
                itemId = ids[1],
                mediaSourceId = null,
                deviceProfile = any(),
                maxStreamingBitrate = 2_000_000,
                startTime = null,
                audioStreamIndex = null,
                subtitleStreamIndex = null,
            )
        }
    }

    // --- skipToIndex --------------------------------------------------------------------------------

    @Test
    fun `skipToIndex rejects an out-of-range index without changing state`() = runTest {
        val ids = List(2) { UUID.randomUUID() }
        setQueue(ids, index = 0)

        queueManager.skipToIndex(-1) shouldBe false
        queueManager.skipToIndex(2) shouldBe false
        queueManager.currentIndex shouldBe 0
    }

    @Test
    fun `skipToIndex jumps directly to the given track`() = runTest {
        val ids = List(3) { UUID.randomUUID() }
        setQueue(ids, index = 0)
        stubSuccessfulResolve(remoteSource(ids[2]))

        queueManager.skipToIndex(2) shouldBe true

        queueManager.currentIndex shouldBe 2
        coVerify {
            mediaSourceResolver.resolveMediaSource(
                itemId = ids[2],
                mediaSourceId = null,
                deviceProfile = any(),
                maxStreamingBitrate = null,
                startTime = null,
                audioStreamIndex = null,
                subtitleStreamIndex = null,
            )
        }
    }

    // --- seekTo -------------------------------------------------------------------------------------

    @Test
    fun `seekTo returns false when nothing is loaded`() = runTest {
        setCurrentMediaSource(null)

        queueManager.seekTo(5_000.milliseconds) shouldBe false
    }

    @Test
    fun `seekTo on a local source seeks the player directly`() = runTest {
        val localSource = mockk<LocalJellyfinMediaSource>(relaxed = true)
        setCurrentMediaSource(localSource)
        val player = mockk<ExoPlayer>(relaxed = true)
        every { viewModel.playerOrNull } returns player

        queueManager.seekTo(5_000.milliseconds) shouldBe true

        verify { player.seekTo(5_000L) }
    }

    @Test
    fun `seekTo on a transcoded stream restarts playback instead of seeking the player`() = runTest {
        val itemId = UUID.randomUUID()
        val current = remoteSource(itemId, maxBitrate = 1_500_000)
        every { current.playMethod } returns PlayMethod.TRANSCODE
        setCurrentMediaSource(current)
        val player = mockk<ExoPlayer>(relaxed = true)
        every { player.playWhenReady } returns true
        every { viewModel.playerOrNull } returns player
        stubSuccessfulResolve(remoteSource(itemId))

        queueManager.seekTo(5_000.milliseconds) shouldBe true

        verify(exactly = 0) { player.seekTo(any<Long>()) }
        coVerify {
            mediaSourceResolver.resolveMediaSource(
                itemId = itemId,
                mediaSourceId = any(),
                deviceProfile = any(),
                maxStreamingBitrate = 1_500_000,
                startTime = 5_000.milliseconds,
                audioStreamIndex = null,
                subtitleStreamIndex = -1,
            )
        }
    }

    @Test
    fun `seekTo on a direct-play stream seeks the player natively`() = runTest {
        val current = remoteSource(UUID.randomUUID())
        setCurrentMediaSource(current)
        val player = mockk<ExoPlayer>(relaxed = true)
        every { viewModel.playerOrNull } returns player

        queueManager.seekTo(5_000.milliseconds) shouldBe true

        verify { player.seekTo(5_000L) }
        coVerify(exactly = 0) {
            mediaSourceResolver.resolveMediaSource(
                itemId = any(),
                mediaSourceId = any(),
                deviceProfile = any(),
                maxStreamingBitrate = any(),
                startTime = any(),
                audioStreamIndex = any(),
                subtitleStreamIndex = any(),
            )
        }
    }

    // --- changeBitrate --------------------------------------------------------------------------------

    @Test
    fun `changeBitrate does nothing without a remote current source`() = runTest {
        setCurrentMediaSource(null)

        queueManager.changeBitrate(1_000_000) shouldBe false
    }

    @Test
    fun `changeBitrate is a no-op that reports success when the bitrate is unchanged`() = runTest {
        val current = remoteSource(UUID.randomUUID(), maxBitrate = 2_000_000)
        setCurrentMediaSource(current)

        queueManager.changeBitrate(2_000_000) shouldBe true

        verify(exactly = 0) { viewModel.getStateAndPause() }
    }

    @Test
    fun `changeBitrate restarts playback at the current position with the new bitrate`() = runTest {
        val itemId = UUID.randomUUID()
        val current = remoteSource(itemId, maxBitrate = 2_000_000)
        setCurrentMediaSource(current)
        every { viewModel.getStateAndPause() } returns PlayState(
            playWhenReady = true,
            position = 30_000.milliseconds,
        )
        stubSuccessfulResolve(remoteSource(itemId))

        queueManager.changeBitrate(4_000_000) shouldBe true

        coVerify {
            mediaSourceResolver.resolveMediaSource(
                itemId = itemId,
                mediaSourceId = any(),
                deviceProfile = any(),
                maxStreamingBitrate = 4_000_000,
                startTime = 30_000.milliseconds,
                audioStreamIndex = null,
                subtitleStreamIndex = -1,
            )
        }
    }

    // --- tryRestartPlayback -----------------------------------------------------------------------

    @Test
    fun `tryRestartPlayback does nothing when nothing is loaded`() {
        setCurrentMediaSource(null)

        queueManager.tryRestartPlayback()

        verify(exactly = 0) { viewModel.load(any(), any(), any()) }
    }

    @Test
    fun `tryRestartPlayback reloads the current source unchanged`() {
        val current = remoteSource(UUID.randomUUID())
        setCurrentMediaSource(current)

        queueManager.tryRestartPlayback()

        verify { viewModel.load(current, any(), true) }
    }
}
