package org.jellyfin.mobile.livetv.playback

import android.app.Application
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The real [LivePlayer], on a real ExoPlayer. Streams are local files that don't exist, so nothing
 * here reaches a server or opens a channel. Errors reaching the listener aren't covered: a real
 * ExoPlayer under Robolectric doesn't get as far as failing to load.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExoLivePlayerTest {

    private val player = ExoLivePlayer(RuntimeEnvironment.getApplication())
    private val missingStream = File(RuntimeEnvironment.getApplication().cacheDir, "missing").toURI().toString()

    @After
    fun tearDown() {
        player.release()
    }

    @Test
    fun `an HLS playlist is played as HLS`() {
        player.play("$missingStream/master.m3u8?api_key=x", channelId = "a")

        player.exoPlayer.currentMediaItem?.localConfiguration?.mimeType shouldBe MimeTypes.APPLICATION_M3U8
    }

    @Test
    fun `a raw transport stream is left for the player to recognise`() {
        player.play("$missingStream/stream.ts", channelId = "a")

        player.exoPlayer.currentMediaItem?.localConfiguration?.mimeType shouldBe null
    }

    @Test
    fun `playing starts as soon as the stream is ready`() {
        player.play("$missingStream/stream.ts", channelId = "a")

        player.exoPlayer.playWhenReady shouldBe true
        player.exoPlayer.playbackState shouldBe Player.STATE_BUFFERING
    }

    @Test
    fun `stopping drops the stream and the video`() {
        player.play("$missingStream/stream.ts", channelId = "a")

        player.stop()

        player.exoPlayer.mediaItemCount shouldBe 0
        player.exoPlayer.playbackState shouldBe Player.STATE_IDLE
        player.videoStarted.value shouldBe false
    }
}
