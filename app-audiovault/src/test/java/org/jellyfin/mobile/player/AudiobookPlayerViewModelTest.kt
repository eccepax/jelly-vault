package org.jellyfin.mobile.player

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.playback.SleepTimer
import org.jellyfin.mobile.common.playback.SleepTimerState
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.playback.AudiobookQueueLoader
import org.jellyfin.mobile.player.playback.AudiobookSleepTimerTarget
import org.jellyfin.mobile.player.playback.AudiobookTrack
import org.jellyfin.mobile.player.playback.FakeAudiobookController
import org.jellyfin.mobile.player.playback.MediaSegmentSkipper
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/** The player screen's view model, over the playback service's controller. */
@OptIn(ExperimentalCoroutinesApi::class)
class AudiobookPlayerViewModelTest {

    private val controller = FakeAudiobookController()
    private val preferences = mockk<AudioVaultAppPreferences>(relaxed = true) {
        every { playbackSpeed } returns 1.25f
    }
    private val book = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val onServer = mutableSetOf(book)

    private fun TestScope.viewModel(): AudiobookPlayerViewModel {
        val loader = AudiobookQueueLoader(
            items = { ids -> ids.filter(onServer::contains).map(::bookNamed) },
            playable = { null },
            coverUrl = { null },
        )
        val sleepTimer = SleepTimer(AudiobookSleepTimerTarget(controller), backgroundScope)
        val segments = MediaSegmentSkipper(controller, { emptyList() }, { mockk() }, backgroundScope)
        return AudiobookPlayerViewModel(controller, loader, sleepTimer, segments, preferences).also { runCurrent() }
    }

    private fun bookNamed(id: UUID) = BaseItemDto(id = id, type = BaseItemKind.AUDIO_BOOK, name = "Book")

    private fun options(vararg ids: UUID, startPositionMs: Long? = null) = PlayOptions(
        ids = ids.toList(),
        mediaSourceId = null,
        startIndex = 0,
        startPosition = startPositionMs?.milliseconds,
        audioStreamIndex = null,
        subtitleStreamIndex = null,
        playFromDownloads = false,
    )

    // --- Starting a book ---------------------------------------------------------------------------

    @Test
    fun `starting a book plays it from where asked`() = runTest {
        val viewModel = viewModel()

        viewModel.start(options(book, startPositionMs = 42_000L)) shouldBe null

        controller.commands shouldBe listOf("play $book from 0 at 42000")
    }

    @Test
    fun `a book that can't be had says why, and plays nothing`() = runTest {
        onServer.clear()
        val viewModel = viewModel()

        viewModel.start(options(book)).shouldBeInstanceOf<PlayerException.NetworkFailure>()

        controller.commands shouldBe emptyList()
    }

    // --- Transport ----------------------------------------------------------------------------------

    @Test
    fun `the transport buttons go to the player`() = runTest {
        val viewModel = viewModel()

        viewModel.togglePlayPause()
        viewModel.rewind()
        viewModel.fastForward()
        viewModel.previousChapter()
        viewModel.nextChapter()
        viewModel.seekTo(5_000L)
        viewModel.skipToTrack(2)

        controller.commands shouldBe listOf(
            "play", "rewind", "fastForward", "previousChapter", "nextChapter", "seekTo 5000", "skipTo 2",
        )
    }

    @Test
    fun `position and length are the player's`() = runTest {
        val viewModel = viewModel()
        controller.playing(AudiobookTrack(id = "a", title = "a", durationMs = 60_000L), positionMs = 1_234L)

        viewModel.positionMs shouldBe 1_234L
        viewModel.durationMs shouldBe 60_000L
    }

    // --- Speed -------------------------------------------------------------------------------------

    @Test
    fun `the speed starts as chosen last time`() = runTest {
        viewModel().speed.value shouldBe 1.25f
    }

    @Test
    fun `choosing a speed plays at it and remembers it`() = runTest {
        val viewModel = viewModel()

        viewModel.setSpeed(1.5f)

        controller.commands shouldBe listOf("speed 1.5")
        viewModel.speed.value shouldBe 1.5f
        verify { preferences.playbackSpeed = 1.5f }
    }

    @Test
    fun `choosing the same speed again does nothing`() = runTest {
        val viewModel = viewModel()

        viewModel.setSpeed(1.25f)

        controller.commands shouldBe emptyList()
        verify(exactly = 0) { preferences.playbackSpeed = any() }
    }

    @Test
    fun `holding fast-forward plays faster, without changing or remembering the speed chosen`() = runTest {
        val viewModel = viewModel()

        viewModel.holdSpeedUp(true)

        controller.commands shouldBe listOf("speed ${Constants.HOLD_SPEEDUP_MULTIPLIER}")
        viewModel.speed.value shouldBe 1.25f
        verify(exactly = 0) { preferences.playbackSpeed = any() }
    }

    @Test
    fun `letting go of fast-forward goes back to the speed chosen`() = runTest {
        val viewModel = viewModel()
        viewModel.holdSpeedUp(true)

        viewModel.holdSpeedUp(false)

        controller.commands.last() shouldBe "speed 1.25"
    }

    // --- Sleep timer and segments ------------------------------------------------------------------

    @Test
    fun `a sleep timer counts down the minutes chosen`() = runTest {
        val viewModel = viewModel()

        viewModel.startSleepTimer(minutes = 15)

        viewModel.sleepTimer.value shouldBe SleepTimerState.Counting(15 * 60_000L)
    }

    @Test
    fun `the sleep timer can wait for the end of the chapter, and be turned off`() = runTest {
        val viewModel = viewModel()

        viewModel.sleepAtEndOfChapter()
        viewModel.sleepTimer.value shouldBe SleepTimerState.EndOfSegment

        viewModel.cancelSleepTimer()
        viewModel.sleepTimer.value shouldBe SleepTimerState.Off
    }

    @Test
    fun `stopping stops the player and the sleep timer`() = runTest {
        val viewModel = viewModel()
        viewModel.startSleepTimer(minutes = 15)

        viewModel.stop()

        controller.commands shouldBe listOf("stop")
        viewModel.sleepTimer.value shouldBe SleepTimerState.Off
    }
}
