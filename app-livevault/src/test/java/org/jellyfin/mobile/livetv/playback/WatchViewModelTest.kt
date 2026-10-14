package org.jellyfin.mobile.livetv.playback

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.livetv.catalog.channel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val log = EventLog()
    private val streams = FakeLiveStreams(log)
    private val player = FakeLivePlayer(log)
    private val ledger = FakeStreamLedger()
    private val bulls = channel("1", "Bulls")
    private val espn = channel("43", "ESPN")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Runs [block] with a view model that's cleared at the end, as the framework would: its scope
     * runs on the test dispatcher, where a playing channel's progress loop never goes idle.
     */
    private fun watchTest(block: suspend TestScope.(WatchViewModel) -> Unit) = runTest(dispatcher) {
        val viewModel = WatchViewModel(streams, StreamCloser(streams, ledger, scope = backgroundScope), player)
        try {
            block(viewModel)
        } finally {
            viewModel.onCleared()
        }
    }

    @Test
    fun `nothing is being watched at first`() = watchTest { viewModel ->

        viewModel.watching.value shouldBe false
    }

    @Test
    fun `watching a channel tunes it and shows its details`() = watchTest { viewModel ->

        viewModel.watch(bulls, listOf(bulls, espn))
        settle()

        viewModel.watching.value shouldBe true
        viewModel.state.value shouldBe WatchUiState(PlaybackState.Playing(bulls.id), bulls, videoStarted = false)
    }

    @Test
    fun `the state follows the video starting`() = watchTest { viewModel ->
        viewModel.watch(bulls, listOf(bulls))
        settle()

        player.videoStarted.value = true
        settle()

        viewModel.state.value.videoStarted shouldBe true
    }

    @Test
    fun `zapping moves through the lineup it was given`() = watchTest { viewModel ->
        viewModel.watch(bulls, listOf(bulls, espn))
        settle()

        viewModel.zap(+1)
        settle()

        viewModel.state.value.channel shouldBe espn
    }

    @Test
    fun `leaving the player closes the stream`() = watchTest { viewModel ->
        viewModel.watch(bulls, listOf(bulls))
        settle()

        viewModel.exit()
        settle()

        viewModel.watching.value shouldBe false
        streams.openStreams.shouldBeEmpty()
        ledger.all().shouldBeEmpty()
    }

    @Test
    fun `the view model going away closes the stream and frees the player`() = watchTest { viewModel ->
        viewModel.watch(bulls, listOf(bulls))
        settle()

        viewModel.onCleared()
        settle()

        player.released shouldBe true
        streams.openStreams.shouldBeEmpty()
        ledger.all().shouldBeEmpty()
    }
}
