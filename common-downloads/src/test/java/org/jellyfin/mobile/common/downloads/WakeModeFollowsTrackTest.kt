package org.jellyfin.mobile.common.downloads

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WakeModeFollowsTrackTest {

    private val modes = mutableListOf<Int>()
    private val follows =
        WakeModeFollowsTrack(isOnDevice = { it.mediaId == "downloaded" }, setWakeMode = { modes += it })

    private fun play(mediaId: String?) = follows.onMediaItemTransition(
        mediaId?.let { MediaItem.Builder().setMediaId(it).build() },
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
    )

    @Test
    fun `a streamed track keeps the network awake while the screen is off`() {
        wakeModeFor(onDevice = false) shouldBe C.WAKE_MODE_NETWORK
    }

    @Test
    fun `a track on the device keeps only the CPU awake, not Wi-Fi`() {
        wakeModeFor(onDevice = true) shouldBe C.WAKE_MODE_LOCAL
    }

    @Test
    fun `each track as it starts keeps awake what it needs`() {
        play("downloaded")
        play("streamed")
        play(null)

        modes shouldBe listOf(C.WAKE_MODE_LOCAL, C.WAKE_MODE_NETWORK, C.WAKE_MODE_NETWORK)
    }
}
