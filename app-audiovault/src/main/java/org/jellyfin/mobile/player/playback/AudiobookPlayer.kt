package org.jellyfin.mobile.player.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline

/**
 * The playback service's player, on either player (this device's, wrapped in a
 * [TranscodeSeekingPlayer], or a cast player). Next and previous move between the playing track's
 * chapters, and on to the next or previous track past the first or last one, so the notification,
 * lock screen, headphones and the app's own chapter buttons all do the same.
 *
 * Each book plays at its own speed ([bookSpeed], by [AudiobookTrack.bookId]; null leaves the speed as
 * it is) as soon as it's the one playing, however it was put in (set or added; by the app, a voice
 * search, another controller). Moving on within a book leaves the speed alone, so a speed held
 * faster stays held.
 *
 * A speed set by any controller (the app's picker, Android Auto, a watch) is kept for the book
 * playing by [keepSpeed], so it's the book's next time too; one only held for a while ([holdAt],
 * holding fast-forward) isn't.
 */
class AudiobookPlayer(
    player: Player,
    private val bookSpeed: (bookId: String) -> Float? = { null },
    private val keepSpeed: (bookId: String, speed: Float) -> Unit = { _, _ -> },
) : OwnCommandsPlayer(player) {

    /** The book whose speed was last put on; it's put on again only once another book plays. */
    private var bookPlaying: String? = null

    init {
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = followBook()
                override fun onTimelineChanged(timeline: Timeline, reason: Int) = followBook()
            },
        )
    }

    private val chapterStarts: List<Long> get() = mediaMetadata.chapterStartsMs

    override fun seekToNext() {
        val nextChapter = chapterStarts.firstOrNull { it > currentPosition }
        when {
            nextChapter != null -> seekTo(nextChapter)
            hasNextMediaItem() -> seekToNextMediaItem()
        }
    }

    /**
     * Back to the start of the chapter playing [PREVIOUS_CHAPTER_WINDOW_MS] ago: a press just after
     * a chapter starts goes to the one before. Within that of the track's start, it works like
     * other players: back a track when only just into it, otherwise back to its start.
     */
    override fun seekToPrevious() {
        val target = currentPosition - PREVIOUS_CHAPTER_WINDOW_MS
        val starts = chapterStarts
        when {
            starts.isNotEmpty() && target >= 0 -> seekTo(starts.lastOrNull { it <= target } ?: 0L)
            currentPosition <= RESTART_WINDOW_MS && hasPreviousMediaItem() -> seekToPreviousMediaItem()
            else -> seekTo(0L)
        }
    }

    // Nothing loaded, or a track from elsewhere with no book: the speed stays as it is
    private fun followBook() {
        val book = currentMediaItem?.mediaMetadata?.bookId ?: return
        if (book == bookPlaying) return
        bookPlaying = book
        bookSpeed(book)?.let(wrappedPlayer::setPlaybackSpeed)
    }

    override fun setPlaybackSpeed(speed: Float) {
        keep(speed)
        super.setPlaybackSpeed(speed)
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        keep(playbackParameters.speed)
        super.setPlaybackParameters(playbackParameters)
    }

    /** Plays at [speed] for now (holding fast-forward), without keeping it for the book. */
    fun holdAt(speed: Float) = wrappedPlayer.setPlaybackSpeed(speed)

    // Kept before it's set, so whoever hears of the change finds it kept already
    private fun keep(speed: Float) {
        val book = currentMediaItem?.mediaMetadata?.bookId ?: return
        if (speed > 0f) keepSpeed(book, speed)
    }

    override fun isCommandAvailable(command: Int): Boolean =
        command in navigationCommands || super.isCommandAvailable(command)

    override fun getAvailableCommands(): Player.Commands {
        val commands = super.getAvailableCommands()
        return if (navigationCommands.isEmpty()) commands else commands.buildUpon().addAll(*navigationCommands).build()
    }

    /**
     * Next and previous, whenever something is loaded: a track may have chapters to move between
     * even alone in the queue. Offered always rather than only then, so what a controller was told
     * can't go stale as the book changes (a controller only sends commands it was told about).
     */
    private val navigationCommands: IntArray
        get() = if (mediaItemCount == 0) {
            IntArray(0)
        } else {
            intArrayOf(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS)
        }

    companion object {
        const val PREVIOUS_CHAPTER_WINDOW_MS = 10_000L
        const val RESTART_WINDOW_MS = 3_000L
    }
}
