package org.jellyfin.mobile.player.sleeptimer

import android.app.Application
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
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
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.interaction.PlayerEvent
import org.jellyfin.mobile.player.mediasegments.MediaSegmentRepository
import org.jellyfin.mobile.player.queue.QueueManager
import org.jellyfin.mobile.player.source.MediaSourceResolver
import org.jellyfin.mobile.player.source.RemoteJellyfinMediaSource
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.hlsSegmentApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.searchApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ChapterInfo
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Regression coverage for [SleepTimerManager]. Backed by a *real* [AudiobookPlayerViewModel]
 * (constructed via the same Koin-module-of-mocks + reflection-injected player/queueManager pattern
 * as [org.jellyfin.mobile.player.AudiobookPlayerViewModelTest]) rather than a wholesale mock of the
 * ViewModel: `startTimer`/`startTimerEndOfChapter` launch on `viewModel.viewModelScope`, and that
 * extension property's real implementation (`getTag`/`setTagIfAbsent` on the real `ViewModel` base
 * class) needs a genuinely-constructed `ViewModel` behind it to work at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SleepTimerManagerTest {

    private val apiClient = mockk<ApiClient>(relaxed = true)
    private val appPreferences = mockk<AudioVaultAppPreferences>(relaxed = true)
    private val mediaSegmentRepository = mockk<MediaSegmentRepository>(relaxed = true)
    private val downloadDao = mockk<DownloadDao>(relaxed = true)
    private val userDao = mockk<UserDao>(relaxed = true)
    private val mediaSourceResolver = mockk<MediaSourceResolver>(relaxed = true)
    private val deviceProfileBuilder = mockk<DeviceProfileBuilder>(relaxed = true)

    private val mockedPlayer = mockk<ExoPlayer>(relaxed = true)
    private val queueManager = mockk<QueueManager>(relaxed = true)

    private lateinit var viewModel: AudiobookPlayerViewModel
    private lateinit var sleepTimerManager: SleepTimerManager

    @Before
    fun setUp() {
        mockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
        every { apiClient.playStateApi } returns mockk(relaxed = true)
        every { apiClient.hlsSegmentApi } returns mockk(relaxed = true)
        every { apiClient.videosApi } returns mockk(relaxed = true)
        every { apiClient.searchApi } returns mockk(relaxed = true)

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
        sleepTimerManager = SleepTimerManager(viewModel)
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
    }

    private fun injectPlayer(viewModel: AudiobookPlayerViewModel, player: ExoPlayer?) {
        val field = AudiobookPlayerViewModel::class.java.getDeclaredField("_player")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        (field.get(viewModel) as androidx.lifecycle.MutableLiveData<ExoPlayer?>).value = player
    }

    private fun injectQueueManager(viewModel: AudiobookPlayerViewModel, queueManager: QueueManager) {
        val field = AudiobookPlayerViewModel::class.java.getDeclaredField("queueManager")
        field.isAccessible = true
        field.set(viewModel, queueManager)
    }

    private fun chapter(startSeconds: Long) = ChapterInfo(
        startPositionTicks = startSeconds * 10_000_000L,
        name = null,
        imagePath = null,
        imageDateModified = LocalDateTime.now(),
        imageTag = null,
    )

    private fun sourceWithChapters(chapters: List<ChapterInfo>?, runTime: Duration = Duration.ZERO): RemoteJellyfinMediaSource {
        val source = mockk<RemoteJellyfinMediaSource>(relaxed = true)
        every { source.item } returns BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO_BOOK,
            name = "Test Book",
            chapters = chapters,
        )
        every { source.runTime } returns runTime
        return source
    }

    // --- fixed-duration timer ------------------------------------------------------------------

    @Test
    fun `no timer is active before one is started`() {
        sleepTimerManager.isActive() shouldBe false
        sleepTimerManager.getRemainingTime() shouldBe null
        sleepTimerManager.getTimerType() shouldBe null
    }

    @Test
    fun `starting a duration timer makes it active with the full duration remaining`() {
        sleepTimerManager.startTimer(SleepTimerManager.SleepTimerDuration.FIFTEEN_MINUTES)

        sleepTimerManager.isActive() shouldBe true
        sleepTimerManager.getTimerType() shouldBe SleepTimerManager.SleepTimerType.DURATION
        val remaining = sleepTimerManager.getRemainingTime()
        requireNotNull(remaining)
        (remaining <= 15.minutes) shouldBe true
        (remaining > 14.minutes) shouldBe true
    }

    @Test
    fun `remaining time counts down as real time passes`() {
        sleepTimerManager.startTimer(SleepTimerManager.SleepTimerDuration.FIFTEEN_MINUTES)
        val initialRemaining = requireNotNull(sleepTimerManager.getRemainingTime())

        Thread.sleep(30)

        val laterRemaining = requireNotNull(sleepTimerManager.getRemainingTime())
        (laterRemaining < initialRemaining) shouldBe true
    }

    @Test
    fun `starting a new timer replaces the previous one`() {
        sleepTimerManager.startTimer(SleepTimerManager.SleepTimerDuration.FIFTEEN_MINUTES)

        sleepTimerManager.startTimer(SleepTimerManager.SleepTimerDuration.SIXTY_MINUTES)

        val remaining = requireNotNull(sleepTimerManager.getRemainingTime())
        (remaining > 15.minutes) shouldBe true
    }

    @Test
    fun `cancelling the timer clears its state entirely`() {
        sleepTimerManager.startTimer(SleepTimerManager.SleepTimerDuration.FIFTEEN_MINUTES)

        sleepTimerManager.cancelTimer()

        sleepTimerManager.isActive() shouldBe false
        sleepTimerManager.getRemainingTime() shouldBe null
        sleepTimerManager.getTimerType() shouldBe null
    }

    @Test
    fun `a duration timer expiring pauses playback and clears itself`() {
        sleepTimerManager.startTimer(SleepTimerManager.SleepTimerDuration.FIFTEEN_MINUTES)

        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMinutes(16))

        verify { viewModel.pause() }
        sleepTimerManager.isActive() shouldBe false
    }

    // --- end-of-chapter timer --------------------------------------------------------------------

    @Test
    fun `end-of-chapter timer does not start without a player`() {
        // Clear the reflection-injected player back out: no player loaded yet.
        injectPlayer(viewModel, player = null)

        sleepTimerManager.startTimerEndOfChapter()

        sleepTimerManager.isActive() shouldBe false
    }

    @Test
    fun `end-of-chapter timer does not start without a current media source`() {
        every { queueManager.getCurrentMediaSourceOrNull() } returns null

        sleepTimerManager.startTimerEndOfChapter()

        sleepTimerManager.isActive() shouldBe false
    }

    @Test
    fun `end-of-chapter timer does not start when the item has no chapters`() {
        every { mockedPlayer.currentPosition } returns 30_000L
        every { queueManager.getCurrentMediaSourceOrNull() } returns sourceWithChapters(chapters = null)

        sleepTimerManager.startTimerEndOfChapter()

        sleepTimerManager.isActive() shouldBe false
    }

    @Test
    fun `end-of-chapter timer targets the start of the next chapter`() {
        every { mockedPlayer.currentPosition } returns 90_000L // 90s, between chapters at 60s and 180s
        val chapters = listOf(chapter(0), chapter(60), chapter(180))
        every { queueManager.getCurrentMediaSourceOrNull() } returns sourceWithChapters(chapters)

        sleepTimerManager.startTimerEndOfChapter()

        sleepTimerManager.isActive() shouldBe true
        sleepTimerManager.getTimerType() shouldBe SleepTimerManager.SleepTimerType.END_OF_CHAPTER
        val remaining = requireNotNull(sleepTimerManager.getRemainingTime())
        // Target is 180s - 90s = 90s away.
        (remaining <= 90.seconds) shouldBe true
        (remaining > 89.seconds) shouldBe true
    }

    @Test
    fun `end-of-chapter timer on a transcoded stream counts to the next chapter of the whole book`() {
        // Resumed at 100s, the transcode starts there: 20s into it is 120s into the book
        AudiobookPlayerViewModel::class.java.getDeclaredField("transcodingStartOffset").apply {
            isAccessible = true
            setLong(viewModel, 100_000L)
        }
        every { mockedPlayer.currentPosition } returns 20_000L
        val chapters = listOf(chapter(0), chapter(60), chapter(180))
        every { queueManager.getCurrentMediaSourceOrNull() } returns sourceWithChapters(chapters)

        sleepTimerManager.startTimerEndOfChapter()

        val remaining = requireNotNull(sleepTimerManager.getRemainingTime())
        // Target is 180s - 120s = 60s away.
        (remaining <= 60.seconds) shouldBe true
        (remaining > 59.seconds) shouldBe true
    }

    @Test
    fun `end-of-chapter timer falls back to the end of the media when already on the last chapter`() {
        every { mockedPlayer.currentPosition } returns 190_000L // past the last chapter (180s)
        val chapters = listOf(chapter(0), chapter(60), chapter(180))
        every { queueManager.getCurrentMediaSourceOrNull() } returns sourceWithChapters(chapters, runTime = 220.seconds)

        sleepTimerManager.startTimerEndOfChapter()

        sleepTimerManager.isActive() shouldBe true
        val remaining = requireNotNull(sleepTimerManager.getRemainingTime())
        // Target is 220s - 190s = 30s away.
        (remaining <= 30.seconds) shouldBe true
        (remaining > 29.seconds) shouldBe true
    }

    @Test
    fun `end-of-chapter timer does not start when the media's total duration isn't known yet`() {
        every { mockedPlayer.currentPosition } returns 190_000L
        val chapters = listOf(chapter(0), chapter(60), chapter(180))
        every { queueManager.getCurrentMediaSourceOrNull() } returns sourceWithChapters(chapters, runTime = Duration.ZERO)

        sleepTimerManager.startTimerEndOfChapter()

        sleepTimerManager.isActive() shouldBe false
    }

    @Test
    fun `end-of-chapter timer does not start when already at or past the end of the media`() {
        every { mockedPlayer.currentPosition } returns 220_000L
        val chapters = listOf(chapter(0), chapter(60), chapter(180))
        every { queueManager.getCurrentMediaSourceOrNull() } returns sourceWithChapters(chapters, runTime = 220.seconds)

        sleepTimerManager.startTimerEndOfChapter()

        sleepTimerManager.isActive() shouldBe false
    }

    @Test
    fun `an end-of-chapter timer expiring pauses playback and clears itself`() {
        every { mockedPlayer.currentPosition } returns 179_900L // 100ms before the next chapter at 180s
        val chapters = listOf(chapter(0), chapter(60), chapter(180))
        every { queueManager.getCurrentMediaSourceOrNull() } returns sourceWithChapters(chapters)

        sleepTimerManager.startTimerEndOfChapter()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500))

        verify { viewModel.pause() }
        sleepTimerManager.isActive() shouldBe false
    }
}
