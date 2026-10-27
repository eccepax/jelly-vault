package org.jellyfin.mobile.cast

import android.app.Application
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
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
    fun `a stream the cast device can't play is reported`() {
        connect()
        var errors = 0
        casting.listenWith { errors++ }

        casting.play(CastStream("https://vault.test/broken", "application/x-mpegURL"), "4.1 Devils", null, null)
        settle()

        errors shouldBe 1
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
