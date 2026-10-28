package org.jellyfin.mobile.music.playback

import android.app.Application
import android.os.Looper
import androidx.media3.common.DeviceInfo
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.run
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.domain.RepeatMode
import org.jellyfin.mobile.music.domain.track
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * [PlayerPlaybackController] while playback is on a cast device: a long queue is held by the player
 * a window at a time (the cast device takes only so much in one message), the queue itself stays
 * whole, and back on this device the player holds all of it again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlayerPlaybackControllerCastTest {

    /** A player that says it's a cast device's while [remote]; its listeners hear when that changes. */
    private class Casting(player: Player) : ForwardingPlayer(player) {
        private val listeners = mutableListOf<Player.Listener>()

        var remote = false
            set(value) {
                field = value
                val events = Player.Events(FlagSet.Builder().add(Player.EVENT_DEVICE_INFO_CHANGED).build())
                listeners.toList().forEach {
                    it.onDeviceInfoChanged(deviceInfo)
                    it.onEvents(this, events)
                }
            }

        /** How many songs it says it holds, if not all: a cast device still sending its queue says fewer. */
        var countShown: Int? = null

        override fun getMediaItemCount(): Int = countShown ?: super.getMediaItemCount()

        override fun getDeviceInfo(): DeviceInfo =
            DeviceInfo.Builder(if (remote) DeviceInfo.PLAYBACK_TYPE_REMOTE else DeviceInfo.PLAYBACK_TYPE_LOCAL).build()

        override fun addListener(listener: Player.Listener) {
            listeners += listener
            super.addListener(listener)
        }

        override fun removeListener(listener: Player.Listener) {
            listeners -= listener
            super.removeListener(listener)
        }
    }

    private lateinit var exo: ExoPlayer
    private lateinit var player: Casting
    private lateinit var controller: PlayerPlaybackController

    private val twelve = (1..12).map { track("t$it") }

    @Before
    fun setUp() {
        exo = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setMediaSourceFactory(TenSecondMediaSourceFactory())
            .build()
        player = Casting(exo)
        controller = PlayerPlaybackController(
            player,
            streamUrl = { "https://vault.test/stream/$it" },
            random = Random(1),
            castWindow = WINDOW,
        )
    }

    @After
    fun tearDown() {
        exo.release()
    }

    private fun settle() {
        advance(exo).untilPendingCommandsAreFullyHandled()
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The player's playlist, as track ids. */
    private fun held(): List<String> = (0 until player.mediaItemCount).map { i ->
        val uid = player.getMediaItemAt(i).queueUid
        controller.state.value.queue.entries.single { it.uid == uid }.track.id
    }

    private val playing: String? get() = held().getOrNull(player.currentMediaItemIndex)

    @Test
    fun `cast, a long queue is held from the playing song, a window long`() {
        player.remote = true

        controller.play(twelve, startIndex = 3)
        settle()

        held() shouldBe listOf("t4", "t5", "t6", "t7")
        playing shouldBe "t4"
        controller.state.value.queue.tracks.size shouldBe 12
        controller.state.value.currentTrack?.id shouldBe "t4"
    }

    @Test
    fun `cast, a queue that fits is held whole`() {
        player.remote = true

        controller.play(twelve.take(WINDOW), startIndex = 1)
        settle()

        held() shouldBe listOf("t1", "t2", "t3", "t4")
        playing shouldBe "t2"
    }

    @Test
    fun `the window is left as it is while songs play through it`() {
        player.remote = true
        controller.play(twelve, startIndex = 3)
        settle()

        run(exo).untilMediaItemIndex(1)
        settle()

        controller.state.value.currentTrack?.id shouldBe "t5"
        held() shouldBe listOf("t4", "t5", "t6", "t7")
        playing shouldBe "t5"
    }

    @Test
    fun `the window's last song playing, the next window is loaded from it`() {
        player.remote = true
        controller.play(twelve, startIndex = 3)
        settle()

        controller.skipTo(6)
        settle()

        held() shouldBe listOf("t7", "t8", "t9", "t10")
        playing shouldBe "t7"
        controller.state.value.currentTrack?.id shouldBe "t7"
    }

    @Test
    fun `a cast device still sending its queue doesn't make a song look like the window's last`() {
        player.remote = true
        controller.play(twelve, startIndex = 3)
        settle()

        player.countShown = 2
        controller.skipToNext()
        settle()
        player.countShown = null

        held() shouldBe listOf("t4", "t5", "t6", "t7")
        playing shouldBe "t5"
    }

    @Test
    fun `the queue's last songs fit in what's held, nothing more is loaded`() {
        player.remote = true
        controller.play(twelve, startIndex = 8)
        settle()

        controller.skipTo(11)
        settle()

        held() shouldBe listOf("t9", "t10", "t11", "t12")
        playing shouldBe "t12"
    }

    @Test
    fun `skipping to a song the player doesn't hold plays it`() {
        player.remote = true
        controller.play(twelve, startIndex = 0)
        settle()

        controller.skipTo(9)
        settle()

        playing shouldBe "t10"
        held() shouldBe listOf("t10", "t11", "t12")
        controller.state.value.currentTrack?.id shouldBe "t10"
    }

    @Test
    fun `next and previous go by the queue`() {
        player.remote = true
        controller.play(twelve, startIndex = 3)
        settle()

        controller.skipToNext()
        settle()
        playing shouldBe "t5"

        controller.skipToPrevious()
        settle()
        playing shouldBe "t4"
        held() shouldBe listOf("t4", "t5", "t6", "t7")
    }

    @Test
    fun `with repeat all, the window wraps round to the start`() {
        player.remote = true
        controller.play(twelve, startIndex = 10)
        controller.cycleRepeatMode()
        settle()

        controller.state.value.queue.repeatMode shouldBe RepeatMode.ALL
        held() shouldBe listOf("t11", "t12", "t1", "t2")
    }

    @Test
    fun `casting a queue held whole cuts it to the window, the playing song carrying on`() {
        controller.play(twelve, startIndex = 3)
        run(exo).untilPosition(3, 2_000L)
        settle()

        player.remote = true
        settle()

        held() shouldBe listOf("t4", "t5", "t6", "t7")
        playing shouldBe "t4"
        (player.currentPosition >= 2_000L) shouldBe true
    }

    @Test
    fun `back on this device, the player holds the whole queue again`() {
        player.remote = true
        controller.play(twelve, startIndex = 3)
        settle()

        player.remote = false
        settle()

        held() shouldBe twelve.map { it.id }
        playing shouldBe "t4"
    }

    @Test
    fun `queue edits while cast keep to the window`() {
        player.remote = true
        controller.play(twelve, startIndex = 3)
        settle()

        controller.playNext(listOf(track("x")))
        settle()

        controller.state.value.queue.tracks.size shouldBe 13
        held() shouldBe listOf("t4", "x", "t5", "t6")
        playing shouldBe "t4"
    }

    private companion object {
        const val WINDOW = 4
    }
}
