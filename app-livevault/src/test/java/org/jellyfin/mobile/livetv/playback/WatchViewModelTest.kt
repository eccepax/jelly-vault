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
import org.jellyfin.mobile.livetv.viewing.FakeViewingMemory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WatchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val log = EventLog()
    private val streams = FakeLiveStreams(log)
    private val player = FakeLivePlayer(log)
    private val cast = FakeLiveCasting(log)
    private val ledger = FakeStreamLedger()
    private val sessions = FakeLiveTvSessions()
    private val memory = FakeViewingMemory()
    private val keepAlive = FakeCastKeepAlive()
    private val watchingNow = WatchingNow()
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
            cast,
            sessions,
            memory,
            keepAlive,
            watchingNow,
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
    fun `signing out stops the channel and closes its stream, once, though the screen lets go of it later`() =
        watchTest { viewModel ->
            viewModel.watch(bulls, listOf(bulls))
            settle()

            watchingNow.stop()
            settle()

            viewModel.watching.value shouldBe false
            viewModel.state.value.playback shouldBe PlaybackState.Idle
            player.playing shouldBe null
            viewModel.onCleared()
            settle()
            log.events.count { it.startsWith("close:") } shouldBe 1
        }

    @Test
    fun `the view model going away closes the stream and frees the player`() = watchTest { viewModel ->
        viewModel.watch(bulls, listOf(bulls))
        settle()

        viewModel.onCleared()
        settle()

        player.released shouldBe true
        cast.released shouldBe true
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

    @Test
    fun `a channel that plays is remembered`() = watchTest { viewModel ->
        viewModel.watch(bulls, listOf(bulls, espn))
        settle()

        memory.lastChannelId.value shouldBe bulls.id
    }

    @Test
    fun `zapping remembers where it lands`() = watchTest { viewModel ->
        viewModel.watch(bulls, listOf(bulls, espn))
        settle()

        viewModel.zap(+1)
        settle()

        memory.lastChannelId.value shouldBe espn.id
    }

    @Test
    fun `a channel that fails is not remembered`() = watchTest { viewModel ->
        viewModel.watch(bulls, listOf(bulls, espn))
        settle()
        streams.failing += espn.id

        viewModel.zap(+1)
        settle()

        memory.lastChannelId.value shouldBe bulls.id
    }

    @Test
    fun `watching while casting plays the channel on the cast device, opened for it`() = watchTest { viewModel ->
        cast.connected.value = true

        viewModel.watch(bulls, listOf(bulls))
        settle()

        log.events.first() shouldBe "open-for-cast:${bulls.id}"
        cast.sent!![2] shouldBe "1 Bulls"
        player.playing shouldBe null
    }

    @Test
    fun `the state names the cast device while casting`() = watchTest { viewModel ->
        viewModel.watch(bulls, listOf(bulls))
        settle()
        viewModel.state.value.castDevice shouldBe null

        cast.deviceName.value = "Main Bedroom TV"
        cast.connected.value = true
        settle()

        viewModel.state.value.castDevice shouldBe "Main Bedroom TV"
    }

    @Test
    fun `a cast device named but not connected yet isn't shown as casting, as the channel still plays here`() =
        watchTest { viewModel ->
            viewModel.watch(bulls, listOf(bulls))
            settle()

            cast.deviceName.value = "Main Bedroom TV"
            settle()

            viewModel.state.value.castDevice shouldBe null
            viewModel.state.value.casting shouldBe false
        }

    @Test
    fun `a cast device connected before it has said its name is still casting`() = watchTest { viewModel ->
        viewModel.watch(bulls, listOf(bulls))
        settle()

        cast.connected.value = true
        settle()

        viewModel.state.value.casting shouldBe true
        viewModel.state.value.castDevice shouldBe null
    }

    @Test
    fun `the video has started once the cast device plays it`() = watchTest { viewModel ->
        cast.connected.value = true
        viewModel.watch(bulls, listOf(bulls))
        settle()

        cast.started.value = true
        settle()

        viewModel.state.value.videoStarted shouldBe true
    }

    @Test
    fun `stopping casting ends the cast`() = watchTest { viewModel ->
        viewModel.stopCasting()

        cast.castsStopped shouldBe 1
    }

    @Test
    fun `the app is kept alive while a channel plays on a cast device, and only then`() = watchTest { viewModel ->
        cast.connected.value = true
        cast.deviceName.value = "Main Bedroom TV"
        keepAlive.holding shouldBe null

        viewModel.watch(bulls, listOf(bulls, espn))
        settle()
        keepAlive.holding shouldBe ("1 Bulls" to "Main Bedroom TV")

        viewModel.exit()
        settle()
        keepAlive.holding shouldBe null
    }

    @Test
    fun `back in the foreground, keeping the app alive is asked for again, as Android may have refused it in the background`() =
        watchTest { viewModel ->
            cast.connected.value = true
            viewModel.watch(bulls, listOf(bulls, espn))
            settle()
            val holds = keepAlive.holds

            viewModel.onBackground()
            viewModel.onForeground()
            settle()

            keepAlive.holds shouldBe holds + 1
            keepAlive.holding shouldBe ("1 Bulls" to null)
        }

    @Test
    fun `a channel playing here keeps nothing alive`() = watchTest { viewModel ->
        viewModel.watch(bulls, listOf(bulls, espn))
        settle()

        keepAlive.holding shouldBe null
    }

    @Test
    fun `a channel stopped on the cast device itself closes the player`() = watchTest { viewModel ->
        cast.connected.value = true
        viewModel.watch(bulls, listOf(bulls, espn))
        settle()

        cast.onStopped!!()
        settle()

        viewModel.watching.value shouldBe false
        keepAlive.holding shouldBe null
    }
}
