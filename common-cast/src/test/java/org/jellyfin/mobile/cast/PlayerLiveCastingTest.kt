package org.jellyfin.mobile.cast

import android.app.Application
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.IOException
import java.time.Duration

/**
 * A live stream on the cast device, for an app with no media session. An ExoPlayer stands in for
 * the cast device; a url ending "broken" fails there.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
@Config(application = Application::class)
class PlayerLiveCastingTest {

    private val device: ExoPlayer = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
        .setMediaSourceFactory(BreakableSourceFactory())
        .build()
    private var castsEnded = 0
    private val castDevice = MutableStateFlow<String?>(null)
    private val casting = PlayerLiveCasting(castDevice, endCast = { castsEnded++ })
    private val stream = CastStream("https://vault.test/live/master.m3u8?ApiKey=t", "application/x-mpegURL")

    @After
    fun tearDown() {
        device.release()
    }

    private fun settle() {
        advance(device).untilPendingCommandsAreFullyHandled()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun connect() {
        casting.start(device, connected = true)
        settle()
    }

    @Test
    fun `nothing is connected until the cast framework has started`() {
        casting.connected.value shouldBe false
    }

    @Test
    fun `a cast device already connected when the framework starts is used`() {
        connect()

        casting.connected.value shouldBe true
    }

    @Test
    fun `connecting and disconnecting a cast device is followed`() {
        casting.start(device, connected = false)

        casting.onConnectionChanged(connected = true)
        casting.connected.value shouldBe true

        casting.onConnectionChanged(connected = false)
        casting.connected.value shouldBe false
    }

    @Test
    fun `playing sends the stream, its type and the channel to the cast device`() {
        connect()

        casting.play(
            stream,
            title = "4.1 Devils",
            subtitle = "Devils at Rangers",
            artworkUri = "https://vault.test/logo.png",
        )
        settle()

        val sent = device.currentMediaItem!!
        sent.localConfiguration?.uri.toString() shouldBe stream.url
        sent.localConfiguration?.mimeType shouldBe "application/x-mpegURL"
        sent.mediaMetadata.title.toString() shouldBe "4.1 Devils"
        sent.mediaMetadata.subtitle.toString() shouldBe "Devils at Rangers"
        sent.mediaMetadata.artworkUri.toString() shouldBe "https://vault.test/logo.png"
        device.playWhenReady shouldBe true
    }

    @Test
    fun `the stream shows as started once the cast device plays it`() {
        connect()

        casting.play(stream, title = "4.1 Devils", subtitle = null, artworkUri = null)
        casting.started.value shouldBe false
        advance(device).untilState(Player.STATE_READY)
        settle()

        casting.started.value shouldBe true
    }

    @Test
    fun `stopping stops the cast device and forgets the stream`() {
        connect()
        casting.play(stream, title = "4.1 Devils", subtitle = null, artworkUri = null)
        settle()

        casting.stop()
        settle()

        device.mediaItemCount shouldBe 0
        device.playbackState shouldBe Player.STATE_IDLE
        casting.started.value shouldBe false
    }

    @Test
    fun `nothing is sent while no cast device is connected`() {
        casting.start(device, connected = false)

        casting.play(stream, title = "4.1 Devils", subtitle = null, artworkUri = null)
        settle()

        device.mediaItemCount shouldBe 0
    }

    @Test
    fun `a stream with nowhere to go is reported as one that couldn't play, once the app has sent it`() {
        casting.start(device, connected = false)
        var errors = 0
        casting.listenWith(onError = { errors++ })

        casting.play(stream, title = "4.1 Devils", subtitle = null, artworkUri = null)

        errors shouldBe 0
        settle()
        errors shouldBe 1
    }

    @Test
    fun `a stream sent before the cast framework has started is reported as one that couldn't play`() {
        var errors = 0
        casting.listenWith(onError = { errors++ })

        casting.play(stream, title = "4.1 Devils", subtitle = null, artworkUri = null)
        settle()

        errors shouldBe 1
    }

    @Test
    fun `a stream with nowhere to go, stopped at once, isn't reported`() {
        var errors = 0
        casting.listenWith(onError = { errors++ })

        casting.play(stream, title = "4.1 Devils", subtitle = null, artworkUri = null)
        casting.stop()
        settle()

        errors shouldBe 0
    }

    @Test
    fun `a stream the cast device can't play is reported`() {
        connect()
        var errors = 0
        casting.listenWith(onError = { errors++ })

        casting.play(CastStream("https://vault.test/broken", "application/x-mpegURL"), "4.1 Devils", null, null)
        settle()

        errors shouldBe 1
    }

    @Test
    fun `a stream stopped on the cast device itself is reported, so the app can free it`() {
        connect()
        var stopped = 0
        casting.listenWith(onError = {}, onStopped = { stopped++ })
        casting.play(stream, title = "4.1 Devils", subtitle = null, artworkUri = null)
        advance(device).untilState(Player.STATE_READY)

        // Its remote's stop, another sender taking over, the stream ending: the cast player stops
        device.stop()
        settle()
        stopped shouldBe 0 // not until it's stayed stopped
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(PlayerLiveCasting.STOPPED_THERE_AFTER_MS))

        stopped shouldBe 1
        casting.started.value shouldBe false
    }

    @Test
    fun `the cast device stopping for a moment isn't reported as stopped there`() {
        connect()
        var stopped = 0
        casting.listenWith(onError = {}, onStopped = { stopped++ })
        casting.play(stream, title = "4.1 Devils", subtitle = null, artworkUri = null)
        advance(device).untilState(Player.STATE_READY)

        // Media3's cast player says it's idle while the cast device's queue is empty for a moment,
        // as it is when the channel changes
        device.stop()
        settle()
        device.prepare()
        advance(device).untilState(Player.STATE_READY)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(PlayerLiveCasting.STOPPED_THERE_AFTER_MS))

        stopped shouldBe 0
    }

    @Test
    fun `a channel playing there isn't taken for stopped while Media3 says idle when asked`() {
        // Media3's cast player says it's idle whenever it's asked while it has no queue from the cast
        // device, which a live stream's may never come: only what it tells its listeners counts
        val saysIdle = object : ForwardingPlayer(device) {
            override fun getPlaybackState() = Player.STATE_IDLE
        }
        casting.start(saysIdle, connected = true)
        settle()
        var stopped = 0
        casting.listenWith(onError = {}, onStopped = { stopped++ })
        casting.play(stream, title = "4.1 Devils", subtitle = null, artworkUri = null)
        advance(device).untilState(Player.STATE_READY)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(PlayerLiveCasting.STOPPED_THERE_AFTER_MS))

        stopped shouldBe 0
    }

    @Test
    fun `the next channel is sent to play at once, not paused`() {
        // Media3's cast player loads an item playing or paused as it is when the item is set: a
        // channel changed after a stop would load paused, and the play after it go nowhere
        val playingWhenSent = mutableListOf<Boolean>()
        val recording = object : ForwardingPlayer(device) {
            override fun setMediaItem(mediaItem: MediaItem) {
                playingWhenSent += playWhenReady
                super.setMediaItem(mediaItem)
            }
        }
        casting.start(recording, connected = true)
        settle()
        casting.play(stream, title = "4.1 Devils", subtitle = null, artworkUri = null)
        advance(device).untilState(Player.STATE_READY)

        casting.stop()
        settle()
        casting.play(stream, title = "4.2 Blackhawks", subtitle = null, artworkUri = null)

        playingWhenSent shouldBe listOf(true, true)
    }

    @Test
    fun `a stop there from before the next channel was sent isn't reported`() {
        connect()
        var stopped = 0
        casting.listenWith(onError = {}, onStopped = { stopped++ })
        casting.play(stream, title = "4.1 Devils", subtitle = null, artworkUri = null)
        advance(device).untilState(Player.STATE_READY)

        device.stop()
        settle()
        casting.play(stream, title = "4.2 Blackhawks", subtitle = null, artworkUri = null)
        advance(device).untilState(Player.STATE_READY)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(PlayerLiveCasting.STOPPED_THERE_AFTER_MS))

        stopped shouldBe 0
    }

    @Test
    fun `stopping it from here isn't reported as stopped there`() {
        connect()
        var stopped = 0
        casting.listenWith(onError = {}, onStopped = { stopped++ })
        casting.play(stream, title = "4.1 Devils", subtitle = null, artworkUri = null)
        advance(device).untilState(Player.STATE_READY)

        casting.stop()
        settle()

        stopped shouldBe 0
    }

    @Test
    fun `the cast device stopping as this app ends the cast isn't reported as stopped there`() {
        connect()
        var stopped = 0
        casting.listenWith(onError = {}, onStopped = { stopped++ })
        casting.play(stream, title = "4.1 Devils", subtitle = null, artworkUri = null)
        advance(device).untilState(Player.STATE_READY)

        // Stop casting, here or from the notification, or signing out: the receiver stops before the session ends
        casting.endingHere()
        device.stop()
        settle()

        stopped shouldBe 0
    }

    @Test
    fun `stopping casting from here isn't reported as stopped there either`() {
        connect()
        var stopped = 0
        casting.listenWith(onError = {}, onStopped = { stopped++ })
        casting.play(stream, title = "4.1 Devils", subtitle = null, artworkUri = null)
        advance(device).untilState(Player.STATE_READY)

        casting.stopCasting()
        device.stop()
        settle()

        stopped shouldBe 0
    }

    @Test
    fun `the live cast player hears of the cast being ended from here, however it's ended`() {
        val framework = WaitingCastPlayers()
        liveCasting(framework)
        framework.start()

        framework.endings.size shouldBe 1
    }

    @Test
    fun `a stream the cast device can't play is reported as that, not as stopped`() {
        connect()
        var errors = 0
        var stopped = 0
        casting.listenWith(onError = { errors++ }, onStopped = { stopped++ })

        casting.play(CastStream("https://vault.test/broken", "application/x-mpegURL"), "4.1 Devils", null, null)
        settle()

        errors shouldBe 1
        stopped shouldBe 0
    }

    @Test
    fun `a stream that fails there as the real cast player does (idle, with no error) is reported as that, not as stopped`() {
        connect()
        var errors = 0
        var stopped = 0
        casting.listenWith(onError = { errors++ }, onStopped = { stopped++ })
        casting.play(stream, title = "4.1 Devils", subtitle = null, artworkUri = null)
        advance(device).untilState(Player.STATE_READY)

        // Media3's cast player only goes idle; the Cast framework's status says it was an error
        device.stop()
        settle()
        casting.failedThere()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(PlayerLiveCasting.STOPPED_THERE_AFTER_MS))

        errors shouldBe 1
        stopped shouldBe 0
        casting.started.value shouldBe false
    }

    @Test
    fun `a failure there with nothing sent from here isn't reported`() {
        connect()
        var errors = 0
        casting.listenWith(onError = { errors++ })
        casting.play(stream, title = "4.1 Devils", subtitle = null, artworkUri = null)
        advance(device).untilState(Player.STATE_READY)
        casting.stop()
        settle()

        casting.failedThere()

        errors shouldBe 0
    }

    @Test
    fun `the live cast player hears of the cast device's failures until it's released`() {
        val framework = WaitingCastPlayers()
        val live = liveCasting(framework)
        framework.start()
        framework.failureListeners.size shouldBe 1

        live.release()

        framework.failureListeners.size shouldBe 0
    }

    @Test
    fun `the stream shows as not started again while the cast device stops playing it (a stall)`() {
        connect()
        casting.play(stream, title = "4.1 Devils", subtitle = null, artworkUri = null)
        advance(device).untilState(Player.STATE_READY)
        settle()
        casting.started.value shouldBe true

        device.pause()
        settle()

        casting.started.value shouldBe false
    }

    @Test
    fun `released before the cast framework starts, nothing is left waiting for it`() {
        val framework = WaitingCastPlayers()
        val live = liveCasting(framework)

        live.release()
        framework.start()

        framework.devicesMade shouldBe 0
    }

    @Test
    fun `forgotten (signing out), what played there is over, as if stopped there, so the app frees it`() {
        val framework = WaitingCastPlayers()
        val live = liveCasting(framework)
        framework.start()
        var stopped = 0
        live.listenWith(onError = {}, onStopped = { stopped++ })

        framework.forgettings.single()()

        stopped shouldBe 1
    }

    @Test
    fun `the live cast player is emptied when the cast is forgotten (signing out)`() {
        val framework = WaitingCastPlayers()
        liveCasting(framework)
        framework.start()

        framework.forgotten.size shouldBe 1
    }

    @Test
    fun `a disconnected cast device has nothing started`() {
        connect()
        casting.play(stream, title = "4.1 Devils", subtitle = null, artworkUri = null)
        settle()

        casting.onConnectionChanged(connected = false)

        casting.started.value shouldBe false
    }

    @Test
    fun `the cast device is named while one is connected`() {
        castDevice.value = "Main Bedroom TV"

        casting.deviceName.value shouldBe "Main Bedroom TV"
    }

    @Test
    fun `stopping casting ends the cast`() {
        connect()

        casting.stopCasting()

        castsEnded shouldBe 1
    }

    @Test
    fun `releasing while connected ends the cast, since nothing could control it after`() {
        connect()

        casting.release()

        castsEnded shouldBe 1
    }

    @Test
    fun `releasing with no cast device connected leaves casting alone`() {
        casting.start(device, connected = false)

        casting.release()

        castsEnded shouldBe 0
    }

    @Test
    fun `a cast player that arrives after release is not used`() {
        casting.release()

        casting.start(device, connected = true)

        casting.connected.value shouldBe false
    }

    /** The Cast framework, started when the test says ([start]); its cast device never connects. */
    private class WaitingCastPlayers : Casting, CastPlayers {
        private val waiting = mutableListOf<() -> Unit>()
        var devicesMade = 0
        val forgotten = mutableListOf<Player>()
        val endings = mutableListOf<() -> Unit>()
        val forgettings = mutableListOf<() -> Unit>()
        val failureListeners = mutableListOf<() -> Unit>()

        override val isAvailable = MutableStateFlow(false)
        override val castDevice = MutableStateFlow<String?>(null)
        override val connectingTo = MutableStateFlow<String?>(null)
        override fun stopCasting() = Unit
        override fun forgetCast() = Unit

        override fun whenReady(onReady: () -> Unit): () -> Unit {
            waiting += onReady
            return { waiting -= onReady }
        }

        fun start() = waiting.toList().forEach { it() }

        override fun castPlayer(local: Player, converter: MediaItemConverter): Player = error("not for live casting")

        override fun remoteCastPlayer(converter: MediaItemConverter, onConnectionChanged: (Boolean) -> Unit): RemotePlayer {
            devicesMade++
            return RemotePlayer(
                TestExoPlayerBuilder(ApplicationProvider.getApplicationContext()).build(),
                connected = false,
            )
        }

        override fun forgetsWithCast(player: Player, endingHere: () -> Unit, forgotten: () -> Unit): () -> Unit {
            this.forgotten += player
            endings += endingHere
            forgettings += forgotten
            return { this.forgotten -= player }
        }

        override fun onCastFailure(onFailure: () -> Unit): () -> Unit {
            failureListeners += onFailure
            return { failureListeners -= onFailure }
        }
    }

    /** Fake sources that play, except a url ending "broken", which fails like a stream the device can't open. */
    private class BreakableSourceFactory : MediaSource.Factory {
        private val playing = FakeAudioSourceFactory()

        override fun createMediaSource(mediaItem: MediaItem): MediaSource =
            if (mediaItem.localConfiguration?.uri.toString().endsWith("broken")) {
                object : FakeMediaSource() {
                    override fun maybeThrowSourceInfoRefreshError() = throw IOException("can't open")
                }.also { it.setAllowPreparation(false) }
            } else {
                playing.createMediaSource(mediaItem)
            }

        override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory = this

        override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory = this

        override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_OTHER)
    }
}
