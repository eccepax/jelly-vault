package org.jellyfin.mobile.player

import android.app.Application
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
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
import org.jellyfin.mobile.common.data.entity.ServerEntity
import org.jellyfin.mobile.common.data.entity.ServerUser
import org.jellyfin.mobile.common.data.entity.UserEntity
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.interaction.PlayerEvent
import org.jellyfin.mobile.player.mediasegments.MediaSegmentRepository
import org.jellyfin.mobile.player.queue.QueueManager
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.player.source.RemoteJellyfinMediaSource
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.extensions.hlsSegmentApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.searchApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.api.operations.SearchApi
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.SearchHint
import org.jellyfin.sdk.model.api.SearchHintResult
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
import kotlin.time.Duration.Companion.milliseconds

/**
 * Regression coverage for [AudiobookPlayerViewModel] — the app's central playback state machine
 * and, until now, its largest completely untested surface (previously only ever mocked wholesale
 * for [org.jellyfin.mobile.player.ui.PlayerFragmentTest]). Constructs a real instance backed by a
 * scoped Koin module of relaxed mocks, then uses reflection to inject a mocked [ExoPlayer] and
 * [QueueManager] — there is no production seam for either, since nothing outside this class has
 * ever needed to fake them before.
 *
 * Deliberately scoped to the control-flow methods reachable without touching [ExoPlayer.Builder],
 * [android.media.session.MediaSession], or [org.jellyfin.mobile.player.interaction.PlayerNotificationHelper]
 * construction (all lazy, and untouched by rewind/fastForward/seek/skip/speed logic) to avoid
 * pulling in real ExoPlayer/notification machinery under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AudiobookPlayerViewModelTest {

    private val apiClient = mockk<ApiClient>(relaxed = true)
    private val appPreferences = mockk<AudioVaultAppPreferences>(relaxed = true)
    private val mediaSegmentRepository = mockk<MediaSegmentRepository>(relaxed = true)
    private val downloadDao = mockk<DownloadDao>(relaxed = true)
    private val userDao = mockk<UserDao>(relaxed = true)
    private val mediaSourceResolver = mockk<MediaSourceResolver>(relaxed = true)
    private val deviceProfileBuilder = mockk<DeviceProfileBuilder>(relaxed = true)
    private val searchApi = mockk<SearchApi>(relaxed = true)

    private val mockedPlayer = mockk<ExoPlayer>(relaxed = true)
    private val queueManager = mockk<QueueManager>(relaxed = true)

    private lateinit var viewModel: AudiobookPlayerViewModel

    @Before
    fun setUp() {
        // apiClient.playStateApi/hlsSegmentApi/videosApi are extension properties with real logic
        // that breaks when called on a plain mock (see ApiClientControllerTest) — stub them directly.
        mockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
        every { apiClient.playStateApi } returns mockk(relaxed = true)
        every { apiClient.hlsSegmentApi } returns mockk(relaxed = true)
        every { apiClient.videosApi } returns mockk(relaxed = true)
        every { apiClient.searchApi } returns searchApi

        startKoin {
            modules(
                module {
                    single { apiClient }
                    single { appPreferences }
                    single { mediaSegmentRepository }
                    single { downloadDao }
                    single { userDao }
                    single { mediaSourceResolver }
                    single { deviceProfileBuilder }
                    single(named(PLAYER_EVENT_CHANNEL)) { Channel<PlayerEvent>() }
                },
            )
        }

        viewModel = AudiobookPlayerViewModel(ApplicationProvider.getApplicationContext())
        injectPlayer(viewModel, mockedPlayer)
        injectQueueManager(viewModel, queueManager)
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /**
     * [AudiobookPlayerViewModel.playFromSearch] hops through a real [kotlinx.coroutines.Dispatchers.IO]
     * thread (twice: resolving the current user, then the search call) before posting back to the
     * main looper — a genuine background thread, not something a single [idle] call can reliably
     * catch. Polls instead of idling once.
     */
    private fun idleUntilSettled() {
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
    }

    private fun injectPlayer(viewModel: AudiobookPlayerViewModel, player: ExoPlayer?) {
        val field = AudiobookPlayerViewModel::class.java.getDeclaredField("_player")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(viewModel) as MutableLiveData<ExoPlayer?>).value = player
    }

    private fun injectQueueManager(viewModel: AudiobookPlayerViewModel, queueManager: QueueManager) {
        val field = AudiobookPlayerViewModel::class.java.getDeclaredField("queueManager")
        field.isAccessible = true
        field.set(viewModel, queueManager)
    }

    // --- rewind / fastForward -------------------------------------------------------------------

    @Test
    fun `rewind seeks back the audiobook skip interval`() {
        every { mockedPlayer.currentPosition } returns 60_000L

        viewModel.rewind()

        verify { mockedPlayer.seekTo(60_000L - Constants.AUDIOBOOK_SKIP_BACK_MS) }
    }

    @Test
    fun `rewind clamps to zero instead of going negative`() {
        every { mockedPlayer.currentPosition } returns 5_000L

        viewModel.rewind()

        verify { mockedPlayer.seekTo(0L) }
    }

    @Test
    fun `fast forward seeks ahead the audiobook skip interval`() {
        every { mockedPlayer.currentPosition } returns 60_000L

        viewModel.fastForward()

        verify { mockedPlayer.seekTo(60_000L + Constants.AUDIOBOOK_SKIP_FORWARD_MS) }
    }

    @Test
    fun `fast forward clamps to the item's total duration instead of overshooting`() {
        val remoteSource = mockk<RemoteJellyfinMediaSource>(relaxed = true)
        every { remoteSource.runTime } returns 70_000.milliseconds
        every { queueManager.getCurrentMediaSourceOrNull() } returns remoteSource
        every { mockedPlayer.currentPosition } returns 60_000L

        viewModel.fastForward()

        verify { mockedPlayer.seekTo(70_000L) }
    }

    @Test
    fun `seeking a transcoded stream restarts the transcode via the queue manager instead of seeking natively`() {
        val remoteSource = mockk<RemoteJellyfinMediaSource>(relaxed = true)
        every { remoteSource.playMethod } returns PlayMethod.TRANSCODE
        every { queueManager.getCurrentMediaSourceOrNull() } returns remoteSource
        every { mockedPlayer.currentPosition } returns 60_000L

        viewModel.rewind()
        idle()

        coVerify { queueManager.seekTo((60_000L - Constants.AUDIOBOOK_SKIP_BACK_MS).milliseconds) }
        verify(exactly = 0) { mockedPlayer.seekTo(any<Long>()) }
    }

    @Test
    fun `seeking a direct-play stream seeks the player natively instead of restarting via the queue manager`() {
        val remoteSource = mockk<RemoteJellyfinMediaSource>(relaxed = true)
        every { remoteSource.playMethod } returns PlayMethod.DIRECT_PLAY
        every { queueManager.getCurrentMediaSourceOrNull() } returns remoteSource
        every { mockedPlayer.currentPosition } returns 60_000L

        viewModel.rewind()

        verify { mockedPlayer.seekTo(60_000L - Constants.AUDIOBOOK_SKIP_BACK_MS) }
        coVerify(exactly = 0) { queueManager.seekTo(any()) }
    }

    // --- skipToPrevious / skipToNext -------------------------------------------------------------

    @Test
    fun `skipping to previous near the start of the track asks the queue manager for the previous item`() {
        every { mockedPlayer.currentPosition } returns Constants.MAX_SKIP_TO_PREV_MS
        coEvery { queueManager.previous() } returns true

        viewModel.skipToPrevious()
        idle()

        coVerify { queueManager.previous() }
        verify(exactly = 0) { mockedPlayer.seekTo(0L) }
    }

    @Test
    fun `skipping to previous with no previous item in the queue restarts the current track instead`() {
        every { mockedPlayer.currentPosition } returns Constants.MAX_SKIP_TO_PREV_MS
        coEvery { queueManager.previous() } returns false

        viewModel.skipToPrevious()
        idle()

        verify { mockedPlayer.seekTo(0L) }
        verify { mockedPlayer.play() }
    }

    @Test
    fun `skipping to previous well into the track restarts it directly without asking the queue manager`() {
        every { mockedPlayer.currentPosition } returns Constants.MAX_SKIP_TO_PREV_MS + 1

        viewModel.skipToPrevious()
        idle()

        verify { mockedPlayer.seekTo(0L) }
        coVerify(exactly = 0) { queueManager.previous() }
    }

    @Test
    fun `skipping to next delegates to the queue manager`() {
        viewModel.skipToNext()
        idle()

        coVerify { queueManager.next() }
    }

    // --- playback speed --------------------------------------------------------------------------

    @Test
    fun `changing the playback speed updates the player and persists the new speed`() {
        every { mockedPlayer.playbackParameters } returns androidx.media3.common.PlaybackParameters(1f)

        val changed = viewModel.setPlaybackSpeed(1.5f)

        changed shouldBe true
        verify { mockedPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(1.5f) }
        verify { appPreferences.playbackSpeed = 1.5f }
    }

    @Test
    fun `setting the same playback speed again is a no-op that does not persist`() {
        every { mockedPlayer.playbackParameters } returns androidx.media3.common.PlaybackParameters(1.5f)

        val changed = viewModel.setPlaybackSpeed(1.5f)

        changed shouldBe false
        verify(exactly = 0) { appPreferences.playbackSpeed = any() }
    }

    @Test
    fun `holding the speed-up button applies the hold speed without persisting it as the base speed`() {
        every { mockedPlayer.playbackParameters } returns androidx.media3.common.PlaybackParameters(1f)

        viewModel.setPressSpeedUp(isPressing = true, speed = Constants.HOLD_SPEEDUP_MULTIPLIER)

        verify {
            mockedPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(Constants.HOLD_SPEEDUP_MULTIPLIER)
        }
        verify(exactly = 0) { appPreferences.playbackSpeed = any() }
    }

    @Test
    fun `releasing the speed-up button restores the previously saved base speed`() {
        every { mockedPlayer.playbackParameters } returns androidx.media3.common.PlaybackParameters(1f)
        viewModel.setPlaybackSpeed(1.75f)

        every {
            mockedPlayer.playbackParameters
        } returns androidx.media3.common.PlaybackParameters(Constants.HOLD_SPEEDUP_MULTIPLIER)
        viewModel.setPressSpeedUp(isPressing = false, speed = Constants.HOLD_SPEEDUP_MULTIPLIER)

        verify { mockedPlayer.playbackParameters = androidx.media3.common.PlaybackParameters(1.75f) }
    }

    // --- skip silence --------------------------------------------------------------------------

    @Test
    fun `enabling skip silence updates the player and persists the preference`() {
        every { mockedPlayer.skipSilenceEnabled } returns false

        val changed = viewModel.setSkipSilenceEnabled(true)

        changed shouldBe true
        verify { mockedPlayer.skipSilenceEnabled = true }
        verify { appPreferences.skipSilenceEnabled = true }
    }

    @Test
    fun `setting skip silence to its current value is a no-op that does not persist`() {
        every { mockedPlayer.skipSilenceEnabled } returns true

        val changed = viewModel.setSkipSilenceEnabled(true)

        changed shouldBe false
        verify(exactly = 0) { appPreferences.skipSilenceEnabled = any() }
    }

    @Test
    fun `setSkipSilenceEnabled returns false when no player is loaded`() {
        injectPlayer(viewModel, null)

        val changed = viewModel.setSkipSilenceEnabled(true)

        changed shouldBe false
        verify(exactly = 0) { appPreferences.skipSilenceEnabled = any() }
    }

    // --- getStateAndPause ------------------------------------------------------------------------

    @Test
    fun `getStateAndPause pauses playback and returns the current position and play-when-ready state`() {
        every { mockedPlayer.playWhenReady } returns true
        every { mockedPlayer.contentPosition } returns 42_000L

        val state = viewModel.getStateAndPause()

        verify { mockedPlayer.pause() }
        state?.playWhenReady shouldBe true
        state?.position shouldBe 42_000.milliseconds
    }

    // --- addSkipAction (media-segment skip race condition) ---------------------------------------

    /**
     * Regression coverage for the flagged race condition: [MediaSegmentRepository.getSegmentsForItem]
     * is an async network fetch, so by the time a SKIP segment's action comes back, playback may
     * already be inside — or past — the segment window. The old code always scheduled a one-shot
     * [ExoPlayer.createMessage] at the segment's start position regardless; a message whose target
     * position has already been passed never fires, silently breaking the skip for any segment that
     * starts near the beginning of a track (exactly the common case for an intro).
     */
    private fun invokeAddSkipAction(mediaSegment: MediaSegmentDto) {
        val method = AudiobookPlayerViewModel::class.java.getDeclaredMethod(
            "addSkipAction",
            MediaSegmentDto::class.java,
        )
        method.isAccessible = true
        method.invoke(viewModel, mediaSegment)
    }

    private fun mediaSegment(startMs: Long, endMs: Long): MediaSegmentDto {
        val segment = mockk<MediaSegmentDto>(relaxed = true)
        every { segment.startTicks } returns startMs.milliseconds.inWholeTicks
        every { segment.endTicks } returns endMs.milliseconds.inWholeTicks
        return segment
    }

    @Test
    fun `a skip segment not yet reached schedules a one-shot seek instead of seeking immediately`() {
        every { mockedPlayer.currentPosition } returns 5_000L

        invokeAddSkipAction(mediaSegment(startMs = 10_000L, endMs = 20_000L))

        verify { mockedPlayer.createMessage(any()) }
        verify(exactly = 0) { mockedPlayer.seekTo(any<Long>()) }
    }

    @Test
    fun `a skip segment the player is already inside is skipped immediately instead of scheduling a message that would never fire`() {
        every { mockedPlayer.currentPosition } returns 15_000L

        invokeAddSkipAction(mediaSegment(startMs = 10_000L, endMs = 20_000L))

        verify { mockedPlayer.seekTo(20_000L) }
        verify(exactly = 0) { mockedPlayer.createMessage(any()) }
    }

    @Test
    fun `a skip segment already fully passed is left alone`() {
        every { mockedPlayer.currentPosition } returns 25_000L

        invokeAddSkipAction(mediaSegment(startMs = 10_000L, endMs = 20_000L))

        verify(exactly = 0) { mockedPlayer.seekTo(any<Long>()) }
        verify(exactly = 0) { mockedPlayer.createMessage(any()) }
    }

    // --- playFromSearch (voice search) ------------------------------------------------------------

    private val serverUser = ServerUser(
        user = UserEntity(
            id = 1L,
            serverId = 1L,
            userId = "11111111-1111-1111-1111-111111111111",
            accessToken = "token",
            lastLoginTimestamp = 0L,
        ),
        server = ServerEntity(id = 1L, hostname = "https://example.com", lastUsedTimestamp = 0L),
    )

    private fun resolvableCurrentUser() {
        every { appPreferences.currentServerId } returns 1L
        every { appPreferences.currentUserId } returns 1L
        every { userDao.getServerUser(1L, 1L) } returns serverUser
    }

    private fun searchHint(itemId: UUID, name: String = "Some Book"): SearchHint {
        val hint = mockk<SearchHint>(relaxed = true)
        every { hint.id } returns itemId
        every { hint.name } returns name
        return hint
    }

    @Test
    fun `playFromSearch with a blank query resumes whatever is already loaded instead of searching`() {
        viewModel.playFromSearch("   ")
        idle()

        verify { mockedPlayer.play() }
        coVerify(exactly = 0) { queueManager.initializePlaybackQueue(any()) }
    }

    @Test
    fun `playFromSearch resolves the top search hit and starts playing it`() {
        resolvableCurrentUser()
        val itemId = UUID.randomUUID()
        val response = mockk<Response<SearchHintResult>>(relaxed = true)
        every { response.content } returns SearchHintResult(listOf(searchHint(itemId)), 1)
        coEvery {
            searchApi.getSearchHints(userId = any(), searchTerm = any(), includeItemTypes = any(), limit = any())
        } returns response

        viewModel.playFromSearch("some book")
        idleUntilSettled()

        coVerify { queueManager.initializePlaybackQueue(match<PlayOptions> { it.ids == listOf(itemId) }) }
    }

    @Test
    fun `playFromSearch does nothing when there are no matching results`() {
        resolvableCurrentUser()
        val response = mockk<Response<SearchHintResult>>(relaxed = true)
        every { response.content } returns SearchHintResult(emptyList(), 0)
        coEvery {
            searchApi.getSearchHints(userId = any(), searchTerm = any(), includeItemTypes = any(), limit = any())
        } returns response

        viewModel.playFromSearch("nonexistent")
        idleUntilSettled()

        coVerify(exactly = 0) { queueManager.initializePlaybackQueue(any()) }
    }

    @Test
    fun `playFromSearch does nothing when the current user cannot be resolved`() {
        every { appPreferences.currentServerId } returns null

        viewModel.playFromSearch("some book")
        idle()

        coVerify(exactly = 0) {
            searchApi.getSearchHints(userId = any(), searchTerm = any(), includeItemTypes = any(), limit = any())
        }
        coVerify(exactly = 0) { queueManager.initializePlaybackQueue(any()) }
    }
}
