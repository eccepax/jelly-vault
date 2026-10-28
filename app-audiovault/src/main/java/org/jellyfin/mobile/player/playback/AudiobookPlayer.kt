package org.jellyfin.mobile.player.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * The playback service's player, on either player (this device's, wrapped in a
 * [TranscodeSeekingPlayer], or a cast player). Next and previous move between the playing track's
 * chapters, and on to the next or previous track past the first or last one, so the notification,
 * lock screen, headphones and the app's own chapter buttons all do the same.
 */
class AudiobookPlayer(player: Player) : ForwardingPlayer(player) {

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
