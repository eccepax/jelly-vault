package org.jellyfin.mobile.music.playback

import android.app.Application
import android.os.Looper
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.run
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jellyfin.mobile.music.domain.RepeatMode
import org.jellyfin.mobile.music.domain.ids
import org.jellyfin.mobile.music.domain.tracks
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Drives a real ExoPlayer (Media3's TestExoPlayer: fake 10 s media, fake auto-advancing clock)
 * through [PlayerPlaybackController], checking the player and our queue never disagree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlayerPlaybackControllerTest {

    private lateinit var player: ExoPlayer
    private lateinit var controller: PlayerPlaybackController

    private val abc = tracks("a", "b", "c")

    @Before
    fun setUp() {
        player = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
            .setMediaSourceFactory(TenSecondMediaSourceFactory())
            .build()
        controller = PlayerPlaybackController(player, streamUrl = { "https://vault.test/stream/$it" }, random = Random(1))
    }

    @After
    fun tearDown() {
        player.release()
    }

    /**
     * The controller's state once the player has delivered pending events. The run helpers return as
     * soon as the player reaches a condition, before listeners (and so the controller) hear about it.
     */
    private val state: PlaybackState
        get() {
            advance(player).untilPendingCommandsAreFullyHandled()
            return controller.state.value
        }

    /** The player's playlist, as track ids, via the queue uids in its media ids. */
    /**
     * Lets the player finish the commands just sent, then delivers the events it queued on the main
     * thread, which is how the controller learns of them. Without the second step a check can run
     * before the controller has caught up: rare, but it happens when the machine is busy.
     */
    private fun settle() {
        advance(player).untilPendingCommandsAreFullyHandled()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun playerTrackIds(): List<String> = (0 until player.mediaItemCount).map { i ->
        val uid = player.getMediaItemAt(i).queueUid
        state.queue.entries.single { it.uid == uid }.track.id
    }

    @Test
    fun `play loads the tracks and starts at the chosen one`() {
        controller.play(abc, startIndex = 1)
        run(player).untilState(Player.STATE_READY)

        playerTrackIds() shouldBe listOf("a", "b", "c")
        player.currentMediaItemIndex shouldBe 1
        state.currentTrack?.id shouldBe "b"
        state.isPlaying shouldBe true
    }

    @Test
    fun `the queue follows the player when a track ends`() {
        controller.play(abc)

        run(player).untilMediaItemIndex(1)

        state.currentTrack?.id shouldBe "b"
    }

    @Test
    fun `the queue follows skips made directly on the player, as from the lock screen`() {
        controller.play(abc)
        run(player).untilState(Player.STATE_READY)

        player.seekToNextMediaItem()
        advance(player).untilPendingCommandsAreFullyHandled()

        state.currentTrack?.id shouldBe "b"
    }

    @Test
    fun `skip to next and previous`() {
        controller.play(abc)
        run(player).untilState(Player.STATE_READY)

        controller.skipToNext()
        controller.skipToNext()
        controller.skipToPrevious()
        advance(player).untilPendingCommandsAreFullyHandled()

        state.currentTrack?.id shouldBe "b"
        player.currentMediaItemIndex shouldBe 1
    }

    @Test
    fun `previous early in a track goes back a track`() {
        controller.play(abc, startIndex = 1)
        run(player).untilPosition(1, 1_000L)

        controller.skipToPrevious()
        advance(player).untilPendingCommandsAreFullyHandled()

        state.currentTrack?.id shouldBe "a"
    }

    @Test
    fun `previous later in a track restarts it`() {
        controller.play(abc, startIndex = 1)
        run(player).untilPosition(1, 5_000L)

        controller.skipToPrevious()
        advance(player).untilPendingCommandsAreFullyHandled()

        state.currentTrack?.id shouldBe "b"
        controller.positionMs shouldBeLessThan PlaybackController.RESTART_THRESHOLD_MS
    }

    @Test
    fun `play and pause toggle`() {
        controller.play(abc)
        run(player).untilState(Player.STATE_READY)

        controller.togglePlayPause()
        run(player).untilPlayWhenReadyIs(false)
        state.isPlaying shouldBe false

        controller.togglePlayPause()
        run(player).untilPlayWhenReadyIs(true)
        run(player).untilState(Player.STATE_READY)
        state.isPlaying shouldBe true
    }

    @Test
    fun `duration is the player's once it knows it, and 0 before`() {
        controller.durationMs shouldBe 0L

        controller.play(abc)
        run(player).untilState(Player.STATE_READY)

        controller.durationMs shouldBe 10_000L
    }

    @Test
    fun `seek moves within the current track`() {
        controller.play(abc)
        run(player).untilState(Player.STATE_READY)

        controller.seekTo(7_000L)
        advance(player).untilPendingCommandsAreFullyHandled()

        controller.positionMs shouldBeGreaterThanOrEqual 7_000L
    }

    @Test
    fun `repeat cycles off, all, one and is applied to the player`() {
        controller.play(abc)

        controller.cycleRepeatMode()
        state.queue.repeatMode shouldBe RepeatMode.ALL
        player.repeatMode shouldBe Player.REPEAT_MODE_ALL

        controller.cycleRepeatMode()
        state.queue.repeatMode shouldBe RepeatMode.ONE
        player.repeatMode shouldBe Player.REPEAT_MODE_ONE

        controller.cycleRepeatMode()
        player.repeatMode shouldBe Player.REPEAT_MODE_OFF
    }

    @Test
    fun `repeat all wraps from the last track to the first`() {
        controller.play(abc, startIndex = 2)
        controller.cycleRepeatMode()

        run(player).untilMediaItemIndex(0)

        state.currentTrack?.id shouldBe "a"
    }

    @Test
    fun `the last track ending with repeat off stops playback`() {
        controller.play(abc, startIndex = 2)

        run(player).untilState(Player.STATE_ENDED)

        state.isPlaying shouldBe false
        state.currentTrack?.id shouldBe "c"
    }

    @Test
    fun `play next slots tracks in after the playing one without interrupting it`() {
        controller.play(abc)
        run(player).untilPosition(0, 4_000L)

        controller.playNext(tracks("x", "y"))
        advance(player).untilPendingCommandsAreFullyHandled()

        state.queue.ids shouldBe listOf("a", "x", "y", "b", "c")
        playerTrackIds() shouldBe state.queue.ids
        state.currentTrack?.id shouldBe "a"
        controller.positionMs shouldBeGreaterThanOrEqual 4_000L
    }

    @Test
    fun `add to queue appends without interrupting`() {
        controller.play(abc)
        run(player).untilPosition(0, 4_000L)

        controller.addToQueue(tracks("x"))
        advance(player).untilPendingCommandsAreFullyHandled()

        playerTrackIds() shouldBe listOf("a", "b", "c", "x")
        controller.positionMs shouldBeGreaterThanOrEqual 4_000L
    }

    @Test
    fun `adding to an empty queue starts playing it`() {
        controller.addToQueue(tracks("x", "y"))
        run(player).untilState(Player.STATE_READY)

        state.currentTrack?.id shouldBe "x"
        state.isPlaying shouldBe true
    }

    @Test
    fun `removing an upcoming track keeps the playing one going`() {
        controller.play(abc)
        run(player).untilPosition(0, 4_000L)

        controller.removeFromQueue(2)
        advance(player).untilPendingCommandsAreFullyHandled()

        playerTrackIds() shouldBe listOf("a", "b")
        state.currentTrack?.id shouldBe "a"
        controller.positionMs shouldBeGreaterThanOrEqual 4_000L
    }

    @Test
    fun `removing the playing track moves on to the next`() {
        controller.play(abc)
        run(player).untilState(Player.STATE_READY)

        controller.removeFromQueue(0)
        advance(player).untilPendingCommandsAreFullyHandled()

        playerTrackIds() shouldBe listOf("b", "c")
        state.currentTrack?.id shouldBe "b"
    }

    @Test
    fun `moving tracks keeps the playing one going`() {
        controller.play(abc)
        run(player).untilPosition(0, 4_000L)

        controller.moveInQueue(from = 2, to = 1)
        advance(player).untilPendingCommandsAreFullyHandled()

        playerTrackIds() shouldBe listOf("a", "c", "b")
        state.currentTrack?.id shouldBe "a"
        controller.positionMs shouldBeGreaterThanOrEqual 4_000L
    }

    @Test
    fun `skipping to a queue position plays it from the start`() {
        controller.play(abc)
        run(player).untilState(Player.STATE_READY)

        controller.skipTo(2)
        advance(player).untilPendingCommandsAreFullyHandled()

        state.currentTrack?.id shouldBe "c"
        player.currentMediaItemIndex shouldBe 2
    }

    @Test
    fun `stop clears the queue and the player`() {
        controller.play(abc)
        run(player).untilState(Player.STATE_READY)

        controller.stop()

        state.queue.tracks shouldBe emptyList()
        state.currentTrack shouldBe null
        state.isPlaying shouldBe false
        player.mediaItemCount shouldBe 0
    }

    @Test
    fun `play shuffled loads a shuffled queue into the player`() {
        val tenTracks = tracks("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")

        controller.playShuffled(tenTracks)
        run(player).untilState(Player.STATE_READY)

        state.queue.isShuffled shouldBe true
        state.queue.ids shouldNotBe tenTracks.map { it.id }
        playerTrackIds() shouldBe state.queue.ids
        player.currentMediaItemIndex shouldBe 0
    }

    @Test
    fun `toggling shuffle mid-track reorders around the playing track without restarting it`() {
        val tenTracks = tracks("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
        controller.play(tenTracks, startIndex = 4)
        run(player).untilPosition(4, 5_000L)
        // Freeze the playhead: the test player's clock races ahead in the background, so a playing track
        // could finish (and the player move on) between the toggle and the assertions below
        controller.pause()
        settle()

        controller.toggleShuffle()
        settle()

        state.queue.isShuffled shouldBe true
        playerTrackIds() shouldBe state.queue.ids
        state.currentTrack?.id shouldBe "4"
        player.currentMediaItemIndex shouldBe state.queue.currentIndex
        controller.positionMs shouldBeGreaterThanOrEqual 5_000L
    }

    @Test
    fun `toggling shuffle off restores the album order and keeps the playing track`() {
        val tenTracks = tracks("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
        controller.play(tenTracks, startIndex = 4)
        run(player).untilState(Player.STATE_READY)
        controller.toggleShuffle()
        controller.skipToNext()
        advance(player).untilPendingCommandsAreFullyHandled()
        val nowPlaying = state.currentTrack?.id

        controller.toggleShuffle()
        settle()

        state.queue.isShuffled shouldBe false
        playerTrackIds() shouldBe tenTracks.map { it.id }
        state.currentTrack?.id shouldBe nowPlaying
        player.currentMediaItemIndex shouldBe state.queue.currentIndex
    }

    @Test
    fun `pause pauses, and pausing again leaves it paused`() {
        controller.play(tracks("a", "b"))

        controller.pause()
        controller.pause()

        player.playWhenReady shouldBe false
    }

    @Test
    fun `volume is the player's volume`() {
        controller.setVolume(0.25f)

        player.volume shouldBe 0.25f
        controller.volume shouldBe 0.25f
    }

    @Test
    fun `playback on a cast device shows as remote, and back here as local`() {
        val castable = Castable(player)
        val castController = PlayerPlaybackController(castable, streamUrl = { "https://vault.test/stream/$it" })
        castController.play(abc)
        settle()
        castController.state.value.isRemote shouldBe false

        castable.remote = true
        castController.togglePlayPause()
        settle()
        castController.state.value.isRemote shouldBe true

        castable.remote = false
        castController.togglePlayPause()
        settle()
        castController.state.value.isRemote shouldBe false
    }

    /** The player as a controller sees it once the session's player is a cast player. */
    private class Castable(player: Player) : ForwardingPlayer(player) {
        var remote = false

        override fun getDeviceInfo(): DeviceInfo =
            if (remote) DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build() else super.getDeviceInfo()
    }
}
