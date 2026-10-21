package org.jellyfin.mobile.player.playback

import android.app.Application
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper.advance
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A transcode is a live stream the player can't seek in, so a seek starts the stream again from the
 * target. Positions still count from the start of the whole track, for the app, the lock screen and
 * what the server hears.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TranscodedAudiobookPlayerTest {

    private val transcodes = FakeAudioSourceFactory(durationMs = 100_000L, isSeekable = false)
    private val exoPlayer: ExoPlayer = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
        .setMediaSourceFactory(transcodes)
        .build()
    private val player = AudiobookPlayer(exoPlayer)

    @After
    fun tearDown() {
        exoPlayer.release()
    }

    private fun load(vararg tracks: AudiobookTrack) {
        player.setMediaItems(tracks.map { it.toMediaItem { id -> "https://vault.test/Audio/$id/universal?api_key=x" } })
        player.prepare()
        settle()
    }

    private fun settle() {
        advance(exoPlayer).untilPendingCommandsAreFullyHandled()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun track(id: String, chapters: List<Chapter> = emptyList()) =
        AudiobookTrack(id = id, title = id, durationMs = 3_600_000L, chapters = chapters)

    private val lastStreamUrl: String get() = transcodes.created.last().localConfiguration!!.uri.toString()

    @Test
    fun `a seek starts the stream again from there`() {
        load(track("a"))

        player.seekTo(1_200_000L)
        settle()

        lastStreamUrl shouldContain "StartTimeTicks=12000000000"
        player.currentPosition shouldBe 1_200_000L
    }

    @Test
    fun `seeking again replaces the start, rather than adding another`() {
        load(track("a"))
        player.seekTo(1_200_000L)
        settle()

        player.seekTo(600_000L)
        settle()

        lastStreamUrl shouldContain "StartTimeTicks=6000000000"
        lastStreamUrl shouldNotContain "12000000000"
        lastStreamUrl shouldContain "api_key=x"
        player.currentPosition shouldBe 600_000L
    }

    @Test
    fun `its length is the whole track's, which the stream itself doesn't know`() {
        load(track("a"))
        player.seekTo(1_200_000L)
        settle()

        player.duration shouldBe 3_600_000L
    }

    @Test
    fun `it counts as seekable, so the lock screen lets you drag through it`() {
        load(track("a"))

        player.isCurrentMediaItemSeekable shouldBe true
    }

    @Test
    fun `skipping back and forward start the stream again too`() {
        load(track("a"))
        player.seekTo(1_200_000L)
        settle()

        player.seekForward()
        settle()
        player.currentPosition shouldBe 1_200_000L + player.seekForwardIncrement

        player.seekBack()
        settle()
        player.currentPosition shouldBe 1_200_000L + player.seekForwardIncrement - player.seekBackIncrement
    }

    @Test
    fun `next chapter goes to the next chapter of the whole track`() {
        load(track("a", listOf(Chapter(0L, null), Chapter(1_000_000L, null), Chapter(2_000_000L, null))))
        player.seekTo(1_200_000L)
        settle()

        player.seekToNext()
        settle()

        player.currentPosition shouldBe 2_000_000L
    }

    @Test
    fun `the next track starts from its beginning`() {
        load(track("a"), track("b"))
        player.seekTo(1_200_000L)
        settle()

        player.seekToNextMediaItem()
        settle()

        player.currentMediaItemIndex shouldBe 1
        player.currentPosition shouldBe 0L
    }

    @Test
    fun `restarting the stream keeps it playing, or paused, as it was`() {
        load(track("a"))
        player.play()
        settle()

        player.seekTo(1_200_000L)
        advance(exoPlayer).untilState(Player.STATE_READY)

        player.playWhenReady shouldBe true
        player.currentPosition shouldBeGreaterThanOrEqual 1_200_000L
    }

    @Test
    fun `going back to a track that was sought in starts it from its beginning`() {
        load(track("a"), track("b"))
        player.seekTo(1_200_000L)
        settle()
        player.seekToNextMediaItem()
        settle()

        player.seekToPreviousMediaItem()
        settle()

        player.currentMediaItemIndex shouldBe 0
        player.currentPosition shouldBe 0L
        lastStreamUrl shouldNotContain AudiobookPlayer.START_PARAM
    }

    @Test
    fun `seeking back again before the restarted stream has loaded still gets there`() {
        load(track("a"))
        player.seekTo(1_200_000L)

        player.seekTo(600_000L)
        settle()

        lastStreamUrl shouldContain "StartTimeTicks=6000000000"
        player.currentPosition shouldBe 600_000L
    }
}
