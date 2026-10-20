package org.jellyfin.mobile.player.playback

import android.app.Application
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Drives a real ExoPlayer (Media3's TestExoPlayer: fake 100 s media, fake auto-advancing clock),
 * behind the service's [AudiobookPlayer], through [PlayerAudiobookController].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlayerAudiobookControllerTest {

    private val exoPlayer: ExoPlayer = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
        .setMediaSourceFactory(FakeAudioSourceFactory(durationMs = 100_000L, failing = setOf("unreachable")))
        .setSeekBackIncrementMs(10_000L)
        .setSeekForwardIncrementMs(30_000L)
        .build()
    private val skipSilence = mutableListOf<Boolean>()
    private val controller = PlayerAudiobookController(
        AudiobookPlayer(exoPlayer),
        streamUrl = { "https://vault.test/Audio/$it/universal" },
        setSkipSilence = { skipSilence += it },
    )

    private val chapters = listOf(Chapter(0L, "One"), Chapter(30_000L, "Two"), Chapter(60_000L, "Three"))

    @After
    fun tearDown() {
        exoPlayer.release()
    }

    private fun track(id: String, chapters: List<Chapter> = emptyList()) =
        AudiobookTrack(id = id, title = "Track $id", durationMs = 100_000L, chapters = chapters)

    private fun settle() {
        advance(exoPlayer).untilPendingCommandsAreFullyHandled()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private val state: AudiobookPlaybackState
        get() {
            settle()
            return controller.state.value
        }

    private fun playPaused(vararg tracks: AudiobookTrack, index: Int = 0, positionMs: Long = 0L) {
        controller.play(tracks.toList(), index, positionMs)
        controller.pause()
        settle()
    }

    @Test
    fun `play loads the tracks and starts the chosen one where it was left`() {
        controller.play(listOf(track("a"), track("b")), startIndex = 1, startPositionMs = 42_000L)

        state.tracks.map { it.id } shouldBe listOf("a", "b")
        state.currentIndex shouldBe 1
        state.currentTrack?.title shouldBe "Track b"
        state.playWhenReady shouldBe true
        // Playing on the test clock, so somewhere past where it started
        exoPlayer.currentPosition shouldBeInRange 42_000L..99_999L
    }

    @Test
    fun `the tracks streams come from the url given`() {
        controller.play(listOf(track("a")), startIndex = 0, startPositionMs = 0L)
        settle()

        exoPlayer.currentMediaItem?.localConfiguration?.uri.toString() shouldContain "/Audio/a/universal"
    }

    @Test
    fun `when a track ends, the next one plays and becomes current`() {
        controller.play(listOf(track("a"), track("b")), startIndex = 0, startPositionMs = 99_000L)

        advance(exoPlayer).untilPositionAtLeast(1, 500L)

        state.currentIndex shouldBe 1
        state.isPlaying shouldBe true
    }

    @Test
    fun `play and pause toggle`() {
        playPaused(track("a"))
        state.playWhenReady shouldBe false

        controller.togglePlayPause()
        state.playWhenReady shouldBe true

        controller.togglePlayPause()
        state.playWhenReady shouldBe false
    }

    @Test
    fun `play after the last track finished starts it again`() {
        controller.play(listOf(track("a")), startIndex = 0, startPositionMs = 99_000L)
        advance(exoPlayer).untilState(Player.STATE_ENDED)

        state.isEnded shouldBe true

        controller.togglePlayPause()

        state.isEnded shouldBe false
        state.playWhenReady shouldBe true
        exoPlayer.currentPosition shouldBeInRange 0L..1_000L
    }

    @Test
    fun `the skip buttons go back 10 seconds and forward 30`() {
        playPaused(track("a"), positionMs = 50_000L)

        controller.rewind()
        settle()
        controller.positionMs shouldBe 40_000L

        controller.fastForward()
        settle()
        controller.positionMs shouldBe 70_000L
    }

    @Test
    fun `the chapter buttons move between chapters`() {
        playPaused(track("a", chapters), positionMs = 45_000L)

        controller.nextChapter()
        settle()
        controller.positionMs shouldBe 60_000L

        controller.previousChapter()
        settle()
        controller.positionMs shouldBe 30_000L
    }

    @Test
    fun `picking a track plays it from its start`() {
        playPaused(track("a"), track("b"), track("c"), positionMs = 50_000L)

        controller.skipTo(2)

        state.currentIndex shouldBe 2
        controller.positionMs shouldBe 0L
    }

    @Test
    fun `seeking goes to a position in the current track`() {
        playPaused(track("a"), track("b"))

        controller.seekTo(77_000L)
        settle()

        controller.positionMs shouldBe 77_000L
    }

    @Test
    fun `the speed is set on the player`() {
        playPaused(track("a"))

        controller.setSpeed(1.75f)
        settle()

        exoPlayer.playbackParameters.speed shouldBe 1.75f
    }

    @Test
    fun `skipping silence goes to the service, the player can't do it through a controller`() {
        controller.setSkipSilence(true)

        skipSilence shouldBe listOf(true)
    }

    @Test
    fun `the volume, for fading out, is the player's`() {
        controller.setVolume(0.25f)

        exoPlayer.volume shouldBe 0.25f
        controller.volume shouldBe 0.25f
    }

    @Test
    fun `stop empties the queue`() {
        playPaused(track("a"), track("b"))

        controller.stop()

        state.tracks.shouldBeEmpty()
        state.currentTrack shouldBe null
        exoPlayer.mediaItemCount shouldBe 0
    }

    @Test
    fun `a playback error shows in the state until the next book starts`() {
        controller.play(listOf(track("unreachable")), startIndex = 0, startPositionMs = 0L)
        advance(exoPlayer).untilPlayerError()

        state.error shouldNotBe null

        controller.play(listOf(track("b")), startIndex = 0, startPositionMs = 0L)
        state.error shouldBe null
    }

    @Test
    fun `the length is the player's once it knows it, and the track's until then`() {
        controller.play(listOf(AudiobookTrack(id = "a", title = "a", durationMs = 3_600_000L)), 0, 0L)
        controller.durationMs shouldBe 3_600_000L

        settle()

        controller.durationMs shouldBe 100_000L
    }
}
