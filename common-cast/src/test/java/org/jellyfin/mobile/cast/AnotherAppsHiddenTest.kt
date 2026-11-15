package org.jellyfin.mobile.cast

import android.app.Application
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The session's player shows nothing of another app's cast: every app on a phone casts in the one
 * session, so without this each app's notification and lock screen would show (and control) the
 * other's book or song.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(UnstableApi::class)
@Config(application = Application::class)
class AnotherAppsHiddenTest {

    private val exo = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
        .setMediaSourceFactory(FakeAudioSourceFactory())
        .build()
    private val hidden = AnotherAppsHidden(exo)

    private val ours = MediaItem.Builder().setMediaId("ours").setUri("https://vault.test/ours").build()
    private val theirs = MediaItem.Builder().setMediaId("theirs").setUri("https://vault.test/theirs").build()
        .markedSentFromAnotherApp()

    @After
    fun tearDown() = exo.release()

    private fun playing(item: MediaItem) {
        exo.setMediaItem(item)
        exo.prepare()
        exo.play()
        advance(exo).untilState(Player.STATE_READY)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `our own item is shown as it is`() {
        playing(ours)

        hidden.currentMediaItem?.mediaId shouldBe "ours"
        hidden.mediaItemCount shouldBe 1
        hidden.isPlaying shouldBe true
        hidden.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) shouldBe true
    }

    @Test
    fun `another app's item is shown as nothing loaded, and can't be controlled`() {
        playing(theirs)

        hidden.currentMediaItem shouldBe null
        hidden.mediaItemCount shouldBe 0
        hidden.currentTimeline.isEmpty shouldBe true
        hidden.playbackState shouldBe Player.STATE_IDLE
        hidden.isPlaying shouldBe false
        hidden.playWhenReady shouldBe false
        hidden.isCommandAvailable(Player.COMMAND_PLAY_PAUSE) shouldBe false
        hidden.availableCommands.contains(Player.COMMAND_SEEK_TO_NEXT) shouldBe false
    }

    @Test
    fun `those listening hear another app's item as nothing loaded too`() {
        val heard = mutableListOf<String>()
        hidden.addListener(
            object : Player.Listener {
                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    heard += "timeline ${timeline.windowCount}"
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    heard += "item ${mediaItem?.mediaId}"
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    heard += "playing $isPlaying"
                }
            },
        )

        playing(theirs)

        ("timeline 0" in heard) shouldBe true
        heard.filter { it == "item theirs" || it == "playing true" || it == "timeline 1" } shouldBe emptyList()
    }

    @Test
    fun `a listener taken off is taken off`() {
        val heard = mutableListOf<String>()
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                heard += "timeline"
            }
        }
        hidden.addListener(listener)
        hidden.removeListener(listener)

        playing(ours)

        heard shouldBe emptyList()
    }
}
