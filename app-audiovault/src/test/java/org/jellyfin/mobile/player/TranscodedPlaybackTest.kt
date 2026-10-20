package org.jellyfin.mobile.player

import android.app.Application
import android.media.session.MediaSession
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.exoplayer.source.MediaSource
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.channels.Channel
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.app.PLAYER_EVENT_CHANNEL
import org.jellyfin.mobile.common.data.dao.UserDao
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.interaction.PlayerEvent
import org.jellyfin.mobile.player.mediasegments.MediaSegmentRepository
import org.jellyfin.mobile.player.queue.QueueManager
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.player.source.RemoteJellyfinMediaSource
import org.jellyfin.mobile.utils.setPlaybackState
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.hlsSegmentApi
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.searchApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ChapterInfo
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.extensions.inWholeTicks
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A transcoded stream starts where the server started the transcode, so the player counts from
 * zero there. Everything the listener sees or aims at (chapters, segments, the lock screen's seek
 * bar) is a position in the whole book, and seeking means restarting the transcode there through
 * the [QueueManager]: the player can't seek inside a transcode. Same harness as
 * [AudiobookPlayerViewModelTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TranscodedPlaybackTest {

    private val apiClient = mockk<ApiClient>(relaxed = true)
    private val mediaSegmentRepository = mockk<MediaSegmentRepository>(relaxed = true)
    private val mockedPlayer = mockk<ExoPlayer>(relaxed = true)
    private val queueManager = mockk<QueueManager>(relaxed = true)
    private val playerEvents = Channel<PlayerEvent>()

    private lateinit var viewModel: AudiobookPlayerViewModel

    @Before
    fun setUp() {
        mockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
        every { apiClient.playStateApi } returns mockk(relaxed = true)
        every { apiClient.hlsSegmentApi } returns mockk(relaxed = true)
        every { apiClient.videosApi } returns mockk(relaxed = true)
        every { apiClient.searchApi } returns mockk(relaxed = true)
        every { apiClient.imageApi } returns mockk(relaxed = true)

        startKoin {
            modules(
                module {
                    single { apiClient }
                    single { mockk<AudioVaultAppPreferences>(relaxed = true) }
                    single { mediaSegmentRepository }
                    single { mockk<DownloadDao>(relaxed = true) }
                    single { mockk<UserDao>(relaxed = true) }
                    single { mockk<MediaSourceResolver>(relaxed = true) }
                    single { mockk<DeviceProfileBuilder>(relaxed = true) }
                    single(named(PLAYER_EVENT_CHANNEL)) { playerEvents }
                },
            )
        }

        viewModel = AudiobookPlayerViewModel(ApplicationProvider.getApplicationContext())
        AudiobookPlayerViewModel::class.java.getDeclaredField("_player").apply {
            isAccessible = true
            @Suppress("UNCHECKED_CAST")
            (get(viewModel) as MutableLiveData<ExoPlayer?>).value = mockedPlayer
        }
        AudiobookPlayerViewModel::class.java.getDeclaredField("queueManager").apply {
            isAccessible = true
            set(viewModel, queueManager)
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun streamed(playMethod: PlayMethod, startTime: Duration = Duration.ZERO, chapters: List<ChapterInfo>? = null) =
        mockk<RemoteJellyfinMediaSource>(relaxed = true) {
            every { this@mockk.playMethod } returns playMethod
            every { this@mockk.startTime } returns startTime
            every { item } returns BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.AUDIO_BOOK,
                name = "Test Book",
                chapters = chapters,
            )
        }

    private fun loadAndPlay(source: RemoteJellyfinMediaSource) {
        every { queueManager.getCurrentMediaSourceOrNull() } returns source
        viewModel.load(source, mockk<MediaSource>(relaxed = true), playWhenReady = true)
    }

    private fun chapter(start: Duration) = ChapterInfo(
        startPositionTicks = start.inWholeTicks,
        name = null,
        imagePath = null,
        imageDateModified = LocalDateTime.now(),
        imageTag = null,
    )

    private val chapters = listOf(chapter(Duration.ZERO), chapter(10.minutes), chapter(20.minutes))

    private fun mediaSegment(startMs: Long, endMs: Long): MediaSegmentDto {
        val segment = mockk<MediaSegmentDto>(relaxed = true)
        every { segment.startTicks } returns startMs.milliseconds.inWholeTicks
        every { segment.endTicks } returns endMs.milliseconds.inWholeTicks
        return segment
    }

    private fun invokeAddSkipAction(mediaSegment: MediaSegmentDto) {
        AudiobookPlayerViewModel::class.java
            .getDeclaredMethod("addSkipAction", MediaSegmentDto::class.java)
            .apply { isAccessible = true }
            .invoke(viewModel, mediaSegment)
    }

    private fun send(event: PlayerEvent) {
        playerEvents.trySend(event).isSuccess shouldBe true
        idle()
    }

    // --- chapters ---------------------------------------------------------------------------------

    @Test
    fun `next chapter on a transcoded stream goes to the next chapter of the whole book`() {
        // Resumed at 12 min, so 1 min into the transcode is 13 min into the book, in chapter 2
        loadAndPlay(streamed(PlayMethod.TRANSCODE, startTime = 12.minutes, chapters = chapters))
        every { mockedPlayer.currentPosition } returns 1.minutes.inWholeMilliseconds

        viewModel.nextChapter()
        idle()

        coVerify { queueManager.seekTo(20.minutes) }
        verify(exactly = 0) { mockedPlayer.seekTo(any<Long>()) }
    }

    @Test
    fun `previous chapter on a transcoded stream goes back to the chapter's start in the whole book`() {
        loadAndPlay(streamed(PlayMethod.TRANSCODE, startTime = 12.minutes, chapters = chapters))
        every { mockedPlayer.currentPosition } returns 3.minutes.inWholeMilliseconds

        viewModel.previousChapter()
        idle()

        coVerify { queueManager.seekTo(10.minutes) }
        verify(exactly = 0) { mockedPlayer.seekTo(any<Long>()) }
    }

    // --- the rest of the player ----------------------------------------------------------------------

    // Resumed 10 min in, the server starts the transcode there and the player counts from zero
    private fun transcodedFrom10Minutes() = loadAndPlay(streamed(PlayMethod.TRANSCODE, startTime = 10.minutes))

    @Test
    fun `previous track, a few seconds into a transcode deep in the book, restarts the track`() {
        transcodedFrom10Minutes()
        every { mockedPlayer.currentPosition } returns 2.seconds.inWholeMilliseconds

        viewModel.skipToPrevious()
        idle()

        coVerify(exactly = 0) { queueManager.previous() }
        coVerify { queueManager.seekTo(Duration.ZERO) }
        verify(exactly = 0) { mockedPlayer.seekTo(any<Long>()) }
    }

    @Test
    fun `skipping a segment of a transcoded stream jumps past its end in the whole book`() {
        transcodedFrom10Minutes()

        viewModel.skipMediaSegment(
            mediaSegment(startMs = 11.minutes.inWholeMilliseconds, endMs = 12.minutes.inWholeMilliseconds),
        )
        idle()

        coVerify { queueManager.seekTo(12.minutes + 1.milliseconds) }
        verify(exactly = 0) { mockedPlayer.seekTo(any<Long>()) }
    }

    @Test
    fun `a skip segment of a transcoded stream that's already playing is skipped in the whole book`() {
        transcodedFrom10Minutes()
        every { mockedPlayer.currentPosition } returns 1.minutes.inWholeMilliseconds

        invokeAddSkipAction(
            mediaSegment(startMs = 10.minutes.inWholeMilliseconds, endMs = 12.minutes.inWholeMilliseconds),
        )
        idle()

        coVerify { queueManager.seekTo(12.minutes) }
        verify(exactly = 0) { mockedPlayer.seekTo(any<Long>()) }
    }

    @Test
    fun `a skip segment of a transcoded stream not reached yet is scheduled at its place in the transcode`() {
        val message = mockk<PlayerMessage>(relaxed = true)
        every { mockedPlayer.createMessage(any()) } returns message
        every { message.setPosition(any<Long>()) } returns message
        every { message.setDeleteAfterDelivery(any()) } returns message
        transcodedFrom10Minutes()
        every { mockedPlayer.currentPosition } returns 0L

        invokeAddSkipAction(
            mediaSegment(startMs = 15.minutes.inWholeMilliseconds, endMs = 16.minutes.inWholeMilliseconds),
        )

        verify { message.setPosition(5.minutes.inWholeMilliseconds) }
    }

    @Test
    fun `a seek command on a transcoded stream seeks in the whole book`() {
        transcodedFrom10Minutes()

        send(PlayerEvent.Seek(30.minutes))

        coVerify { queueManager.seekTo(30.minutes) }
        verify(exactly = 0) { mockedPlayer.seekTo(any<Long>()) }
    }

    @Test
    fun `the lock screen shows a transcoded stream's position in the whole book`() {
        transcodedFrom10Minutes()
        every { mockedPlayer.currentPosition } returns 30.seconds.inWholeMilliseconds
        every { mockedPlayer.playbackState } returns Player.STATE_BUFFERING
        val shown = slot<Long>()
        mockkStatic(MEDIA_EXTENSIONS)
        try {
            every { any<MediaSession>().setPlaybackState(any<Player>(), any(), capture(shown)) } just Runs

            viewModel.onPlaybackStateChanged(Player.STATE_BUFFERING)
        } finally {
            unmockkStatic(MEDIA_EXTENSIONS)
        }

        shown.captured shouldBe (10.minutes + 30.seconds).inWholeMilliseconds
    }
}

private const val MEDIA_EXTENSIONS = "org.jellyfin.mobile.utils.MediaExtensionsKt"
