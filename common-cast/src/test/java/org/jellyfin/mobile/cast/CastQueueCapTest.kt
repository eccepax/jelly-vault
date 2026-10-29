package org.jellyfin.mobile.cast

import android.app.Application
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.run
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A session's cast player never sends a cast device more than it takes in one message. */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
@Config(application = Application::class)
class CastQueueCapTest {

    private val exo = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
        .setMediaSourceFactory(FakeAudioSourceFactory())
        .build()

    /** Stands in for the cast device's player. */
    private val castDevice = object : ForwardingPlayer(exo) {
        override fun getDeviceInfo(): DeviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build()
    }
    private val cap = CastQueueCap(castDevice)

    private fun songs(range: IntRange) = range.map {
        MediaItem.Builder().setMediaId(
            "s$it",
        ).setUri("https://vault.test/s$it").build()
    }

    private fun held() = (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it).mediaId }

    @After
    fun tearDown() {
        exo.release()
    }

    @Test
    fun `a long queue set from a song goes from that song, a limit long`() {
        cap.setMediaItems(songs(0 until 300), 250, 0L)

        held().first() shouldBe "s250"
        held().size shouldBe 50
        cap.setMediaItems(songs(0 until 300), 10, 0L)
        held() shouldBe songs(10 until 10 + CAST_QUEUE_LIMIT).map { it.mediaId }
    }

    @Test
    fun `a long queue set to carry on goes from the song playing, where it was`() {
        cap.setMediaItems(songs(40 until 60), 5, 0L)
        cap.prepare()
        run(exo).untilPosition(5, 500L)
        shadowOf(Looper.getMainLooper()).idle()

        val was = exo.currentPosition
        // Another controller sets a long queue holding it, keeping the position
        cap.setMediaItems(songs(0 until 300), false)
        advance(exo).untilPendingCommandsAreFullyHandled()

        held().first() shouldBe "s45"
        exo.currentMediaItem?.mediaId shouldBe "s45"
        (was > 0L) shouldBe true
        exo.currentPosition shouldBe was
    }

    @Test
    fun `a long queue set to start over goes from its first song`() {
        cap.setMediaItems(songs(40 until 60), 5, 0L)

        cap.setMediaItems(songs(0 until 300), true)

        held().first() shouldBe "s0"
    }

    @Test
    fun `songs added past the limit are dropped`() {
        cap.setMediaItems(songs(0 until CAST_QUEUE_LIMIT - 1), 0, 0L)

        cap.addMediaItems(songs(1000 until 1005))

        held().size shouldBe CAST_QUEUE_LIMIT
        held().last() shouldBe "s1000"
    }
}
