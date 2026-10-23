package org.jellyfin.mobile.player

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The player screen's view model, over the playback service's controller. */
@OptIn(ExperimentalCoroutinesApi::class)
class AudiobookPlayerViewModelTest {

    private val controller = FakeAudiobookController()
    private val preferences = mockk<AudioVaultAppPreferences>(relaxed = true) {
        every { playbackSpeed } returns 1.25f
    }
    private val book = UUID.fromString("00000000-0000-4000-8000-000000000001")
    private val onServer = mutableSetOf(book)

    private fun TestScope.viewModel(
        items: suspend (List<UUID>) -> List<BaseItemDto> = { ids -> ids.filter(onServer::contains).map(::bookNamed) },
    ): AudiobookPlayerViewModel {
        val loader = AudiobookQueueLoader(
            items = items,
            playable = { null },
            coverUrl = { null },
        )
        val sleepTimer = SleepTimer(AudiobookSleepTimerTarget(controller), backgroundScope)
        val segments = MediaSegmentSkipper(controller, { emptyList() }, { mockk() }, backgroundScope)
        return AudiobookPlayerViewModel(controller, loader, sleepTimer, segments, preferences).also { runCurrent() }
    }

    @BeforeEach
    fun setMain() {
        // play() starts the book in the view model's scope, so it outlives a rotation
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun resetMain() {
        Dispatchers.resetMain()
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

        viewModel.play(options(book, startPositionMs = 42_000L))

        controller.commands shouldBe listOf("play $book from 0 at 42000")
    }

    /** Kept until the player screen is there to hear it: it may be covered, or the app in the background. */
    @Test
    fun `a book that can't be had says why, once heard, and plays nothing`() = runTest {
        onServer.clear()
        val viewModel = viewModel()

        viewModel.play(options(book))

        viewModel.startFailures.first().shouldBeInstanceOf<PlayerException.NetworkFailure>()
        controller.commands shouldBe emptyList()
    }

    @Test
    fun `a failure is dropped once another book is asked for`() = runTest {
        val viewModel = viewModel()
        onServer.clear()
        viewModel.play(options(book))
        onServer += book

        viewModel.play(options(book))

        withTimeoutOrNull(1.seconds) { viewModel.startFailures.first() } shouldBe null
    }

    @Test
    fun `once the player screen's left, a failure isn't kept, even one still on its way`() = runTest {
        val loading = CompletableDeferred<Unit>()
        val viewModel = viewModel(items = {
            loading.await()
            emptyList()
        })
        onServer.clear()
        viewModel.play(options(book))
        viewModel.play(options(book))

        viewModel.forgetStartFailures()
        loading.complete(Unit)
        runCurrent()

        withTimeoutOrNull(1.seconds) { viewModel.startFailures.first() } shouldBe null
    }

    /** As when back's pressed on the player while the book's still loading: it mustn't start later, unseen. */
    @Test
    fun `a book given up on while it loads never plays`() = runTest {
        val loading = CompletableDeferred<Unit>()
        val viewModel = viewModel(items = { ids ->
            loading.await()
            ids.map(::bookNamed)
        })
        viewModel.play(options(book))

        viewModel.forgetStartFailures()
        loading.complete(Unit)
        runCurrent()

        controller.commands shouldBe emptyList()
    }

    @Test
    fun `of two books asked for, only the last plays, even when the first loads last`() = runTest {
        val second = UUID.fromString("00000000-0000-4000-8000-000000000002")
        onServer += second
        val loads = mapOf(book to CompletableDeferred<Unit>(), second to CompletableDeferred())
        val viewModel = viewModel(items = { ids ->
            loads.getValue(ids.single()).await()
            ids.map(::bookNamed)
        })
        viewModel.play(options(book))
        viewModel.play(options(second))

        loads.getValue(second).complete(Unit)
        runCurrent()
        loads.getValue(book).complete(Unit)
        runCurrent()

        controller.commands shouldBe listOf("play $second from 0 at 0")
    }

    @Test
    fun `whether there's a book to show is followed only while the player screen follows it`() = runTest {
        val viewModel = viewModel()
        val followers = controller.state.subscriptionCount
        val others = followers.value

        val following = backgroundScope.launch(
            UnconfinedTestDispatcher(testScheduler),
        ) { viewModel.presence.collect {} }
        followers.value shouldBe others + 1
        following.cancel()
        runCurrent()
        followers.value shouldBe others
    }

    @Test
    fun `a book is starting from being asked for until it plays`() = runTest {
        val loading = CompletableDeferred<Unit>()
        val viewModel = viewModel(items = { ids ->
            loading.await()
            ids.map(::bookNamed)
        })
        viewModel.presenceNow.starting shouldBe false

        viewModel.play(options(book))
        viewModel.presenceNow.starting shouldBe true
        loading.complete(Unit)
        runCurrent()

        viewModel.presenceNow.starting shouldBe false
    }

    @Test
    fun `with another book playing, a book is starting until it's the one playing`() = runTest {
        val other = AudiobookTrack(id = "other", title = "Other", durationMs = 60_000L)
        controller.playing(other)
        controller.playsLater = true
        val viewModel = viewModel()

        viewModel.play(options(book))
        viewModel.presenceNow.starting shouldBe true

        // The service tells of the book asked for
        controller.playing(AudiobookTrack(id = book.toString(), title = "Book", durationMs = 0L))
        runCurrent()
        viewModel.presenceNow.starting shouldBe false
    }

    @Test
    fun `a book that fails stays starting, the player screen leaving on the failure itself`() = runTest {
        onServer.clear()
        val viewModel = viewModel()

        viewModel.play(options(book))

        viewModel.startFailures.first()
        viewModel.presenceNow.starting shouldBe true
    }

    @Test
    fun `a book that never comes to play is given up on after a while, stopped, and said why`() = runTest {
        controller.playsLater = true
        val viewModel = viewModel()

        viewModel.play(options(book))
        advanceTimeBy(AudiobookPlayerViewModel.START_TIMEOUT_MS - 1)
        controller.commands shouldBe listOf("play $book from 0 at 0")

        advanceTimeBy(2)
        controller.commands shouldBe listOf("play $book from 0 at 0", "stop")
        viewModel.startFailures.first().shouldBeInstanceOf<PlayerException.StartTimedOut>()
        // The screen leaves on the failure, once heard, not on there being nothing to show
        viewModel.presenceNow shouldBe PlayerPresence(hasBook = false, starting = true)
    }

    @Test
    fun `a book that comes to play in time isn't given up on`() = runTest {
        controller.playsLater = true
        val viewModel = viewModel()

        viewModel.play(options(book))
        advanceTimeBy(AudiobookPlayerViewModel.START_TIMEOUT_MS - 1)
        controller.playing(AudiobookTrack(id = book.toString(), title = "Book", durationMs = 0L))
        advanceTimeBy(AudiobookPlayerViewModel.START_TIMEOUT_MS)

        controller.commands shouldBe listOf("play $book from 0 at 0")
        withTimeoutOrNull(1.seconds) { viewModel.startFailures.first() } shouldBe null
    }

    @Test
    fun `a book overtaken by another isn't given up on for it`() = runTest {
        controller.playsLater = true
        val viewModel = viewModel()
        viewModel.play(options(book))
        advanceTimeBy(AudiobookPlayerViewModel.START_TIMEOUT_MS / 2)

        viewModel.forgetStartFailures()
        advanceTimeBy(AudiobookPlayerViewModel.START_TIMEOUT_MS)

        controller.commands shouldBe listOf("play $book from 0 at 0")
    }

    @Test
    fun `the book arriving is seen at once with its starting over, never as nothing to show`() = runTest {
        controller.playsLater = true
        val viewModel = viewModel()
        val seen = mutableListOf<PlayerPresence>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.presence.collect { seen += it } }

        viewModel.play(options(book))
        controller.playing(AudiobookTrack(id = book.toString(), title = "Book", durationMs = 0L))
        runCurrent()

        seen shouldBe listOf(
            PlayerPresence(hasBook = false, starting = false),
            PlayerPresence(hasBook = false, starting = true),
            PlayerPresence(hasBook = true, starting = false),
        )
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
    fun `the progress is the player's position and length`() = runTest {
        val viewModel = viewModel()
        controller.playing(AudiobookTrack(id = "a", title = "a", durationMs = 60_000L), positionMs = 1_234L)

        viewModel.progress.first() shouldBe PlayerProgress(positionMs = 1_234L, durationMs = 60_000L)
    }

    @Test
    fun `the progress follows the player as it moves`() = runTest {
        val viewModel = viewModel()
        val track = AudiobookTrack(id = "a", title = "a", durationMs = 60_000L)
        controller.playing(track, positionMs = 1_000L)
        val seen = mutableListOf<Long>()
        val following = backgroundScope.launch { viewModel.progress.collect { seen += it.positionMs } }
        runCurrent()

        controller.playing(track, positionMs = 2_000L)
        advanceTimeBy(1.seconds)

        seen.first() shouldBe 1_000L
        seen.last() shouldBe 2_000L
        following.cancel()
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
