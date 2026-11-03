package org.jellyfin.mobile.player.playback

import android.app.Application
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
 * The service's player: next and previous move between a book's chapters, the way the app's chapter
 * buttons always have, so the notification, lock screen and headphones do the same. Tracks here
 * last 100 s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AudiobookPlayerTest {

    private val sources = FakeAudioSourceFactory(durationMs = 100_000L)
    private val exoPlayer: ExoPlayer = TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
        .setMediaSourceFactory(sources)
        .build()
    private val player = AudiobookPlayer(TranscodeSeekingPlayer(exoPlayer))

    private val chapters = listOf(Chapter(0L, "One"), Chapter(30_000L, "Two"), Chapter(60_000L, "Three"))

    @After
    fun tearDown() {
        exoPlayer.release()
    }

    private fun track(id: String, chapters: List<Chapter> = emptyList(), book: String? = null) =
        AudiobookTrack(id = id, title = id, durationMs = 100_000L, chapters = chapters, bookId = book)

    private fun item(track: AudiobookTrack) = track.toMediaItem { id -> "https://vault.test/$id" }

    private fun load(vararg tracks: AudiobookTrack, index: Int = 0, positionMs: Long = 0L) {
        player.setMediaItems(tracks.map { it.toMediaItem { id -> "https://vault.test/$id" } }, index, positionMs)
        player.prepare()
        settle()
    }

    private fun settle() {
        advance(exoPlayer).untilPendingCommandsAreFullyHandled()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun at(index: Int, positionMs: Long) {
        player.seekTo(index, positionMs)
        settle()
    }

    private fun where(): Pair<Int, Long> {
        settle()
        return player.currentMediaItemIndex to player.currentPosition
    }

    @Test
    fun `next jumps to the start of the next chapter`() {
        load(track("a", chapters), track("b"))
        at(0, 10_000L)

        player.seekToNext()

        where() shouldBe (0 to 30_000L)
    }

    @Test
    fun `next in the last chapter moves on to the next track`() {
        load(track("a", chapters), track("b"))
        at(0, 70_000L)

        player.seekToNext()

        where() shouldBe (1 to 0L)
    }

    @Test
    fun `next in a track without chapters moves on to the next track`() {
        load(track("a"), track("b"))
        at(0, 10_000L)

        player.seekToNext()

        where() shouldBe (1 to 0L)
    }

    @Test
    fun `previous well into a chapter goes back to its start`() {
        load(track("a", chapters))
        at(0, 45_000L)

        player.seekToPrevious()

        where() shouldBe (0 to 30_000L)
    }

    @Test
    fun `previous just after a chapter starts goes back to the chapter before`() {
        load(track("a", chapters))
        at(0, 35_000L)

        player.seekToPrevious()

        where() shouldBe (0 to 0L)
    }

    @Test
    fun `previous at the very start of a book's chapters goes to the previous track`() {
        load(track("a"), track("b", chapters))
        at(1, 2_000L)

        player.seekToPrevious()

        where() shouldBe (0 to 0L)
    }

    @Test
    fun `previous a few seconds into the first chapter, with no track before, restarts it`() {
        load(track("a", chapters))
        at(0, 5_000L)

        player.seekToPrevious()

        where() shouldBe (0 to 0L)
    }

    @Test
    fun `previous near the start of a track without chapters goes to the previous track`() {
        load(track("a"), track("b"))
        at(1, 2_000L)

        player.seekToPrevious()

        where() shouldBe (0 to 0L)
    }

    @Test
    fun `previous well into a track without chapters restarts it`() {
        load(track("a"), track("b"))
        at(1, 50_000L)

        player.seekToPrevious()

        where() shouldBe (1 to 0L)
    }

    /**
     * Always, once something is loaded, so what a controller was told can't go stale as the book
     * changes: the app's MediaController only sends commands the session said were available.
     */
    @Test
    fun `next and previous are always offered once a book is loaded, even a single track`() {
        load(track("a"))

        player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT) shouldBe true
        player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS) shouldBe true
        player.availableCommands.contains(Player.COMMAND_SEEK_TO_NEXT) shouldBe true
    }

    @Test
    fun `next at the end of the last chapter of the last track does nothing`() {
        load(track("a", chapters))
        at(0, 90_000L)

        player.seekToNext()

        where() shouldBe (0 to 90_000L)
    }

    // --- Speed per book ----------------------------------------------------------------------------

    private val speeds = mapOf("slow narrator" to 1.5f, "fast narrator" to 0.9f)
    private val speedy = AudiobookPlayer(TranscodeSeekingPlayer(exoPlayer)) { speeds[it] }

    @Test
    fun `each book plays at its own speed as it's put in`() {
        speedy.setMediaItems(listOf(item(track("1", book = "slow narrator")), item(track("2", book = "slow narrator"))))
        settle()
        exoPlayer.playbackParameters.speed shouldBe 1.5f

        speedy.setMediaItems(listOf(item(track("3", book = "fast narrator"))), 0, 0L)
        settle()
        exoPlayer.playbackParameters.speed shouldBe 0.9f
    }

    @Test
    fun `a book started from a later part plays at the book's speed`() {
        speedy.setMediaItem(item(track("part 2", book = "slow narrator")))
        settle()

        exoPlayer.playbackParameters.speed shouldBe 1.5f
    }

    @Test
    fun `a book added to an emptied queue, rather than set, plays at its speed too`() {
        speedy.clearMediaItems()
        speedy.addMediaItems(listOf(item(track("1", book = "fast narrator"))))
        settle()

        exoPlayer.playbackParameters.speed shouldBe 0.9f
    }

    @Test
    fun `moving on to the book's next part leaves the speed as it is, such as held faster`() {
        speedy.setMediaItems(listOf(item(track("1", book = "slow narrator")), item(track("2", book = "slow narrator"))))
        speedy.prepare()
        settle()
        speedy.setPlaybackSpeed(3f)

        speedy.seekToNextMediaItem()
        settle()

        exoPlayer.playbackParameters.speed shouldBe 3f
    }

    @Test
    fun `a book it knows no speed for, or a track from elsewhere with no book, leaves the speed as it is`() {
        exoPlayer.setPlaybackSpeed(1.25f)

        speedy.setMediaItem(item(track("a", book = "unknown")))
        settle()
        exoPlayer.playbackParameters.speed shouldBe 1.25f

        speedy.setMediaItem(item(track("b")))
        settle()
        exoPlayer.playbackParameters.speed shouldBe 1.25f
    }
}
