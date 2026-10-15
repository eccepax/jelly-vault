package org.jellyfin.mobile.player

import android.app.Application
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
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
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.player.source.RemoteJellyfinMediaSource
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.hlsSegmentApi
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.searchApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.api.operations.HlsSegmentApi
import org.jellyfin.sdk.api.operations.PlayStateApi
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
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
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * What [AudiobookPlayerViewModel] tells the server (streamed books) and the download store
 * (downloaded books) about where the listener is: the resume position the whole app depends on.
 * Same harness as [AudiobookPlayerViewModelTest]: a real view model with a mocked [ExoPlayer] and
 * [QueueManager] put in by reflection.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlaybackPositionReportingTest {

    private val apiClient = mockk<ApiClient>(relaxed = true)
    private val playStateApi = mockk<PlayStateApi>(relaxed = true)
    private val hlsSegmentApi = mockk<HlsSegmentApi>(relaxed = true)
    private val downloadDao = mockk<DownloadDao>(relaxed = true)

    private val player = mockk<ExoPlayer>(relaxed = true)
    private val queueManager = mockk<QueueManager>(relaxed = true)

    private val itemId = UUID.randomUUID()
    private val streamed = mockk<RemoteJellyfinMediaSource>(relaxed = true) {
        every { itemId } returns this@PlaybackPositionReportingTest.itemId
        every { playSessionId } returns "session-1"
        every { playMethod } returns PlayMethod.DIRECT_PLAY
        every { runTime } returns 60.minutes
        every { startTime } returns Duration.ZERO
        every { item } returns null
    }
    private val downloaded = mockk<LocalJellyfinMediaSource>(relaxed = true) {
        every { id } returns "downloaded-1"
        every { item } returns null
    }

    private lateinit var viewModel: AudiobookPlayerViewModel

    @Before
    fun setUp() {
        // The api client's *Api properties are extensions with real logic; stub them directly.
        mockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
        every { apiClient.playStateApi } returns playStateApi
        every { apiClient.hlsSegmentApi } returns hlsSegmentApi
        every { apiClient.videosApi } returns mockk(relaxed = true)
        every { apiClient.searchApi } returns mockk(relaxed = true)
        every { apiClient.imageApi } returns mockk(relaxed = true)

        startKoin {
            modules(
                module {
                    single { apiClient }
                    single { mockk<AudioVaultAppPreferences>(relaxed = true) }
                    single { mockk<MediaSegmentRepository>(relaxed = true) }
                    single { downloadDao }
                    single { mockk<UserDao>(relaxed = true) }
                    single { mockk<MediaSourceResolver>(relaxed = true) }
                    single { mockk<DeviceProfileBuilder>(relaxed = true) }
                    single(named(PLAYER_EVENT_CHANNEL)) { Channel<PlayerEvent>() }
                },
            )
        }

        viewModel = AudiobookPlayerViewModel(ApplicationProvider.getApplicationContext())
        @Suppress("UNCHECKED_CAST")
        (field("_player").get(viewModel) as MutableLiveData<ExoPlayer?>).value = player
        field("queueManager").set(viewModel, queueManager)

        every { player.playbackState } returns Player.STATE_READY
        every { player.playWhenReady } returns true
        every { player.isPlaying } returns true
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
    }

    // --- Streamed books ---------------------------------------------------------------------------

    @Test
    fun `stopping partway reports where the listener stopped`() {
        playing(streamed, at = 95.seconds)

        viewModel.stop()
        settle()

        coVerify { playStateApi.reportPlaybackStopped(stopInfo(at = 95.seconds)) }
        coVerify(exactly = 0) { playStateApi.markPlayedItem(any(), any(), any()) }
    }

    @Test
    fun `finishing a book reports it stopped at its end and marks it played`() {
        playing(streamed, at = 59.minutes)
        every { player.playbackState } returns Player.STATE_ENDED

        viewModel.onPlaybackStateChanged(Player.STATE_ENDED)
        settle()

        coVerify { playStateApi.reportPlaybackStopped(stopInfo(at = 60.minutes)) }
        coVerify { playStateApi.markPlayedItem(itemId, any(), any()) }
    }

    @Test
    fun `a transcoded stream reports the position in the whole book, not in the transcode`() {
        // The server starts a transcode at the resume point, so the player counts from zero there.
        every { streamed.playMethod } returns PlayMethod.TRANSCODE
        every { streamed.startTime } returns 10.minutes
        every { queueManager.getCurrentMediaSourceOrNull() } returns streamed
        viewModel.load(streamed, mockk<MediaSource>(relaxed = true), playWhenReady = true)
        every { player.currentPosition } returns 30.seconds.inWholeMilliseconds

        viewModel.stop()
        settle()

        coVerify { playStateApi.reportPlaybackStopped(stopInfo(at = 10.minutes + 30.seconds)) }
    }

    @Test
    fun `stopping a transcoded stream ends its transcode on the server`() {
        every { streamed.playMethod } returns PlayMethod.TRANSCODE
        playing(streamed, at = 95.seconds)

        viewModel.stop()
        settle()

        coVerify { hlsSegmentApi.stopEncodingProcess(any(), "session-1") }
    }

    @Test
    fun `stopping a direct-play stream leaves the server's transcoder alone`() {
        playing(streamed, at = 95.seconds)

        viewModel.stop()
        settle()

        coVerify(exactly = 0) { hlsSegmentApi.stopEncodingProcess(any(), any()) }
    }

    @Test
    fun `a server that can't take the stop report doesn't crash the app`() {
        coEvery { playStateApi.reportPlaybackStopped(any()) } throws InvalidStatusException(503)
        playing(streamed, at = 95.seconds)

        viewModel.stop()
        settle()

        coVerify { playStateApi.reportPlaybackStopped(any()) }
    }

    @Test
    fun `pausing reports the paused position to the server`() {
        playing(streamed, at = 2.minutes)
        every { player.isPlaying } returns false
        every { player.playWhenReady } returns false

        viewModel.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        settle()

        coVerify { playStateApi.reportPlaybackProgress(progressInfo(at = 2.minutes, paused = true)) }
    }

    @Test
    fun `while playing, progress is reported to the server every ten seconds`() {
        playing(streamed, at = 2.minutes)
        viewModel.onPlaybackStateChanged(Player.STATE_READY)
        settle()
        coVerify(exactly = 1) { playStateApi.reportPlaybackProgress(any()) }

        every { player.currentPosition } returns (2.minutes + 10.seconds).inWholeMilliseconds
        advance(Constants.PLAYER_TIME_UPDATE_RATE.milliseconds)

        coVerify(exactly = 2) { playStateApi.reportPlaybackProgress(any()) }
        coVerify { playStateApi.reportPlaybackProgress(progressInfo(at = 2.minutes + 10.seconds, paused = false)) }
    }

    @Test
    fun `once paused, the periodic progress reports stop`() {
        playing(streamed, at = 2.minutes)
        viewModel.onPlaybackStateChanged(Player.STATE_READY)
        settle()
        every { player.isPlaying } returns false
        every { player.playWhenReady } returns false
        viewModel.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        settle()

        advance(Constants.PLAYER_TIME_UPDATE_RATE.milliseconds * 3)

        // One when playback became ready, one for the pause, none after.
        coVerify(exactly = 2) { playStateApi.reportPlaybackProgress(any()) }
    }

    @Test
    fun `a playback error reports the stop, so the server doesn't keep a dead session`() {
        playing(streamed, at = 95.seconds)

        viewModel.onPlayerError(
            PlaybackException("boom", null, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED),
        )
        settle()

        coVerify { playStateApi.reportPlaybackStopped(stopInfo(at = 95.seconds)) }
        viewModel.error.value shouldBe "boom"
    }

    // --- Downloaded books -------------------------------------------------------------------------

    @Test
    fun `pausing a downloaded book saves where it was paused, without telling the server`() {
        playing(downloaded, at = 2.minutes)
        every { player.isPlaying } returns false
        every { player.playWhenReady } returns false

        viewModel.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        settle()

        verify { downloaded.startTime = 2.minutes }
        coVerify { downloadDao.insert(match { it.mediaSource === downloaded }) }
        coVerify(exactly = 0) { playStateApi.reportPlaybackProgress(any()) }
    }

    @Test
    fun `stopping a downloaded book partway saves where it stopped`() {
        playing(downloaded, at = 95.seconds)

        viewModel.stop()
        settle()

        verify { downloaded.startTime = 95.seconds }
        coVerify { downloadDao.insert(match { it.mediaSource === downloaded }) }
        coVerify(exactly = 0) { playStateApi.reportPlaybackStopped(any()) }
    }

    @Test
    fun `finishing a downloaded book saves it to start over next time`() {
        playing(downloaded, at = 59.minutes)
        every { player.playbackState } returns Player.STATE_ENDED

        viewModel.onPlaybackStateChanged(Player.STATE_ENDED)
        settle()

        verify { downloaded.startTime = Duration.ZERO }
        coVerify { downloadDao.insert(match { it.mediaSource === downloaded }) }
    }

    // --- Helpers ----------------------------------------------------------------------------------

    private fun playing(source: JellyfinMediaSource, at: Duration) {
        every { queueManager.getCurrentMediaSourceOrNull() } returns source
        every { player.currentPosition } returns at.inWholeMilliseconds
    }

    private fun MockKMatcherScope.stopInfo(at: Duration) = match<PlaybackStopInfo> {
        it.itemId == itemId && it.playSessionId == "session-1" && it.positionTicks == at.inWholeTicks
    }

    private fun MockKMatcherScope.progressInfo(at: Duration, paused: Boolean) = match<PlaybackProgressInfo> {
        it.itemId == itemId && it.positionTicks == at.inWholeTicks && it.isPaused == paused
    }

    /**
     * Reports hop to [kotlinx.coroutines.Dispatchers.IO] (a real thread) and back to the main
     * looper, which a single idle can't catch, so this idles until they've had time to land.
     */
    private fun settle() {
        repeat(SETTLE_ROUNDS) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(SETTLE_SLEEP_MS)
        }
    }

    private fun advance(time: Duration) {
        shadowOf(Looper.getMainLooper()).idleFor(time.toJavaDuration())
        settle()
    }

    private fun field(name: String) =
        AudiobookPlayerViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }
}

private const val SETTLE_ROUNDS = 50
private const val SETTLE_SLEEP_MS = 5L
