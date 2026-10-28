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
    private val player = AudiobookPlayer(TranscodeSeekingPlayer(exoPlayer))

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
    fun `wrapping a cast player, where a stream starts and how it seeks are the cast player's`() {
        // CastQueuePlayer asks for each stream from where it should start, and counts from the track's start
        val onCast = AudiobookPlayer(exoPlayer)
        val partWay = track("a").toMediaItem { "https://vault.test/Audio/$it/universal?StartTimeTicks=600000000" }
        onCast.setMediaItems(listOf(partWay))
        onCast.prepare()
        settle()
        val streams = transcodes.created.size

        onCast.currentPosition shouldBe exoPlayer.currentPosition
        onCast.seekTo(30_000L)
        settle()

        transcodes.created.size shouldBe streams
        exoPlayer.currentPosition shouldBe 30_000L
    }

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
        lastStreamUrl shouldNotContain TranscodeSeekingPlayer.START_PARAM
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

    @Test
    fun `a queue set from part way into a transcode starts its stream there`() {
        // As playback coming back from a cast device does, or a book resumed part way
        player.setMediaItems(listOf(item("a")), 0, 90_000L)
        player.prepare()
        settle()

        lastStreamUrl shouldContain "StartTimeTicks=900000000"
        player.currentPosition shouldBe 90_000L
    }

    @Test
    fun `a seek before the stream has opened still gets there, once it turns out to be a transcode`() {
        player.setMediaItems(listOf(item("a")))
        player.prepare()

        player.seekTo(300_000L)
        settle()

        lastStreamUrl shouldContain "StartTimeTicks=3000000000"
        player.currentPosition shouldBe 300_000L
    }

    @Test
    fun `a queue set again is played from its tracks' places, not where their streams last started`() {
        // Items back from a cast device still carry the start of a stream this device had restarted
        val partWay = item("a", "StartTimeTicks=600000000")

        player.setMediaItems(listOf(partWay, item("b", "StartTimeTicks=600000000")), 0, 0L)
        player.prepare()
        settle()

        lastStreamUrl shouldNotContain TranscodeSeekingPlayer.START_PARAM
        player.currentPosition shouldBe 0L
        player.getMediaItemAt(1).localConfiguration!!.uri.toString() shouldNotContain TranscodeSeekingPlayer.START_PARAM
    }

    @Test
    fun `a queue set any other way is played from its tracks' places too`() {
        player.setMediaItems(listOf(item("a", "StartTimeTicks=600000000")))
        player.addMediaItems(listOf(item("b", "StartTimeTicks=600000000")))
        player.addMediaItem(0, item("c", "StartTimeTicks=600000000"))
        player.prepare()
        settle()

        (0 until player.mediaItemCount).forEach {
            player.getMediaItemAt(it).localConfiguration!!.uri.toString() shouldNotContain TranscodeSeekingPlayer.START_PARAM
        }
        player.setMediaItem(item("d", "StartTimeTicks=600000000"), true)
        settle()
        lastStreamUrl shouldNotContain TranscodeSeekingPlayer.START_PARAM
    }

    @Test
    fun `a place asked for in a queue that never opened isn't where the next queue starts`() {
        player.setMediaItems(listOf(item("a")), 0, 90_000L)

        player.setMediaItems(listOf(item("b")))
        player.prepare()
        settle()

        lastStreamUrl shouldNotContain TranscodeSeekingPlayer.START_PARAM
        player.currentPosition shouldBe 0L
    }

    @Test
    fun `going to the start of a transcode still opening doesn't start it again`() {
        player.setMediaItems(listOf(item("a")))
        player.prepare()

        player.seekTo(0L)
        settle()

        transcodes.created.size shouldBe 1
        player.currentPosition shouldBe 0L
    }

    @Test
    fun `wrapping a cast player, items started part way are left as they are`() {
        // The cast player asks for its own streams: rewriting its items' uris would only make it resend them
        val castPlayer = TestExoPlayerBuilder(
            ApplicationProvider.getApplicationContext(),
        ).setMediaSourceFactory(transcodes).build()
        val onCast = AudiobookPlayer(castPlayer)
        onCast.setMediaItems(listOf(item("a", "StartTimeTicks=600000000"), item("b")))
        onCast.prepare()
        advance(castPlayer).untilPendingCommandsAreFullyHandled()

        onCast.seekToNextMediaItem()
        advance(castPlayer).untilPendingCommandsAreFullyHandled()

        onCast.getMediaItemAt(0).localConfiguration!!.uri.toString() shouldContain "StartTimeTicks=600000000"
        castPlayer.release()
    }

    private fun item(id: String, query: String = "") =
        track(
            id,
        ).toMediaItem { "https://vault.test/Audio/$it/universal?api_key=x${if (query.isEmpty()) "" else "&$query"}" }
}
