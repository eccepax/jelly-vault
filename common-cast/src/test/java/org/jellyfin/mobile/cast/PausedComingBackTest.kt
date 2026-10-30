package org.jellyfin.mobile.cast

import android.app.Application
import androidx.annotation.OptIn
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Playback coming back here paused, and carrying on when cast again soon after. */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
@Config(application = Application::class)
class PausedComingBackTest {

    private var clock = 0L
    private val transfer = PausedComingBack { clock }

    private fun exo(): ExoPlayer = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
        .setMediaSourceFactory(FakeAudioSourceFactory())
        .build()

    private val here = exo()
    private val thereExo = exo()

    /** Stands in for the cast device's player. */
    private val there = object : ForwardingPlayer(thereExo) {
        override fun getDeviceInfo(): DeviceInfo = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build()
    }

    @After
    fun tearDown() {
        here.release()
        thereExo.release()
    }

    private fun playingThenComingBack() {
        thereExo.setMediaItems(listOf(MediaItem.Builder().setMediaId("1").build()))
        thereExo.prepare()
        thereExo.play()
        advance(thereExo).untilPendingCommandsAreFullyHandled()
        transfer.transferState(there, here)
        advance(here).untilPendingCommandsAreFullyHandled()
    }

    @Test
    fun `back here it waits paused`() {
        playingThenComingBack()

        here.playWhenReady shouldBe false
    }

    @Test
    fun `cast again soon after, it plays on there`() {
        playingThenComingBack()
        clock += 10_000

        transfer.transferState(here, there)

        there.playWhenReady shouldBe true
    }

    @Test
    fun `cast again soon after but played and paused here meanwhile, it stays paused`() {
        playingThenComingBack()
        here.play()
        advance(here).untilPendingCommandsAreFullyHandled()
        here.pause()
        advance(here).untilPendingCommandsAreFullyHandled()
        clock += 10_000

        transfer.transferState(here, there)

        there.playWhenReady shouldBe false
    }

    @Test
    fun `cast again long after, it stays paused`() {
        playingThenComingBack()
        clock += 60_000

        transfer.transferState(here, there)

        there.playWhenReady shouldBe false
    }
}
