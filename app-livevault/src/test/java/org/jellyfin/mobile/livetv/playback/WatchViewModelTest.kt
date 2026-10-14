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
    private val sessions = FakeLiveTvSessions()
    private val bulls = channel("1", "Bulls")
    private val espn = channel("43", "ESPN")
    private val tubi = channel("5001", "FREE| Movies", tags = listOf("Free"))
    private val roku = ChannelSession(deviceName = "Living Room", userName = "root", channelName = "ESPN")

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
        val viewModel = WatchViewModel(
            streams,
            StreamCloser(streams, ledger, scope = backgroundScope),
            player,
            sessions,
        )
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

    @Test
    fun `a free channel that fails just says so`() = watchTest { viewModel ->
        streams.failing += tubi.id
        sessions.watching = listOf(roku)

        viewModel.watch(tubi, listOf(tubi))
        settle()

        viewModel.state.value.playback shouldBe PlaybackState.Failed(tubi.id)
        viewModel.state.value.loginInUse shouldBe null
    }

    @Test
    fun `a provider channel that fails says the login is in use, and by whom`() = watchTest { viewModel ->
        streams.failing += espn.id
        val tablet = ChannelSession(deviceName = "Tablet", userName = "kid", channelName = "FREE| Cartoons")
        sessions.watching = listOf(roku, tablet)

        viewModel.watch(espn, listOf(espn))
        settle()

        viewModel.state.value.playback shouldBe PlaybackState.Failed(espn.id)
        viewModel.state.value.loginInUse shouldBe LoginInUse(holders = listOf(roku))
    }

    @Test
    fun `the login is still said to be in use when the holder is not on the Vault`() = watchTest { viewModel ->
        streams.failing += espn.id

        viewModel.watch(espn, listOf(espn))
        settle()

        viewModel.state.value.loginInUse shouldBe LoginInUse(holders = emptyList())
    }

    @Test
    fun `the login is still said to be in use when the sessions cannot be read`() = watchTest { viewModel ->
        streams.failing += espn.id
        sessions.failure = java.io.IOException("offline")

        viewModel.watch(espn, listOf(espn))
        settle()

        viewModel.state.value.loginInUse shouldBe LoginInUse(holders = emptyList())
    }

    @Test
    fun `retrying clears the login message`() = watchTest { viewModel ->
        streams.failing += espn.id
        viewModel.watch(espn, listOf(espn))
        settle()
        streams.failing -= espn.id

        viewModel.retry()
        settle()

        viewModel.state.value.playback shouldBe PlaybackState.Playing(espn.id)
        viewModel.state.value.loginInUse shouldBe null
    }
}
