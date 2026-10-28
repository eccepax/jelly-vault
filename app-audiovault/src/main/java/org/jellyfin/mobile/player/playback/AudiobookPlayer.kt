package org.jellyfin.mobile.player.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline

/**
 * The playback service's player. Next and previous move between the playing track's chapters, and
 * on to the next or previous track past the first or last one, so the notification, lock screen,
 * headphones and the app's own chapter buttons all do the same.
 *
 * A transcode is a live stream the player can't seek in, so seeking in one starts the stream again
 * from the target (the server transcodes from [START_PARAM]). Positions and the length still count
 * from the start of the whole track, for everything that reads them. A track started part way is
 * put back to its start once it's left, so going back to it later plays it from its beginning.
 * Whether a stream is a transcode is only known once it's open: a place asked for before then (a
 * queue set from part way, a seek while it loads) is where a transcode starts once it turns out to
 * be one. A queue set anew starts its streams from their beginnings, whatever their uris said.
 *
 * Without [streamOffsets] (wrapping a cast player, which starts and seeks its own streams and counts
 * from the track's start: see common-cast's CastQueuePlayer) only the chapters are this one's.
 */
class AudiobookPlayer(player: Player, private val streamOffsets: Boolean = true) : ForwardingPlayer(player) {

    /** A place asked for (the item's index, and where in it) before its stream was open. */
    private var pendingStart: Pair<Int, Long>? = null

    init {
        if (streamOffsets) {
            player.addListener(
                object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = restoreLeftTracks()

                    override fun onTimelineChanged(timeline: Timeline, reason: Int) = startPending()
                },
            )
        }
    }

    private val chapterStarts: List<Long> get() = mediaMetadata.chapterStartsMs

    /** Where the playing stream starts in its track: past 0 when a transcode was started part way. */
    private val streamStartMs: Long get() = if (streamOffsets) currentMediaItem?.streamStartMs ?: 0L else 0L

    /**
     * A transcode: one the player has opened and can't seek in, or one already started part way,
     * which only a transcode is (it's still loading, so the player doesn't know yet).
     */
    private val isLiveTranscode: Boolean
        get() {
            if (!streamOffsets) return false
            if (streamStartMs > 0) return true
            val timeline = currentTimeline
            if (timeline.isEmpty) return false
            val window = timeline.getWindow(currentMediaItemIndex, Timeline.Window())
            return !window.isPlaceholder && !window.isSeekable
        }

    override fun getCurrentPosition(): Long = super.getCurrentPosition() + streamStartMs

    override fun getContentPosition(): Long = super.getContentPosition() + streamStartMs

    override fun getBufferedPosition(): Long = super.getBufferedPosition() + streamStartMs

    override fun getContentBufferedPosition(): Long = super.getContentBufferedPosition() + streamStartMs

    override fun getDuration(): Long {
        val trackMs = mediaMetadata.durationMs
        return if (isLiveTranscode && trackMs != null) trackMs else super.getDuration()
    }

    override fun getContentDuration(): Long = duration

    override fun isCurrentMediaItemSeekable(): Boolean = isLiveTranscode || super.isCurrentMediaItemSeekable()

    override fun seekTo(positionMs: Long) {
        when {
            isLiveTranscode -> restartStreamAt(positionMs)
            else -> {
                if (streamOffsets && isOpening) pendingStart = currentMediaItemIndex to positionMs
                super.seekTo(positionMs - streamStartMs)
            }
        }
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        if (!streamOffsets) return super.setMediaItems(mediaItems, startIndex, startPositionMs)
        super.setMediaItems(mediaItems.map { it.fromItsStart() }, startIndex, startPositionMs)
        pendingStart = (startIndex to startPositionMs).takeIf { startPositionMs > 0 }
    }

    /** Whether the playing item's stream isn't open yet, so whether it can seek isn't known. */
    private val isOpening: Boolean
        get() {
            val timeline = currentTimeline
            return timeline.isEmpty || timeline.getWindow(currentMediaItemIndex, Timeline.Window()).isPlaceholder
        }

    /** The stream asked for part way is open: a transcode, which played from its start, starts there instead. */
    private fun startPending() {
        val (index, positionMs) = pendingStart ?: return
        if (currentMediaItemIndex != index) {
            pendingStart = null
            return
        }
        if (isOpening) return
        pendingStart = null
        if (isLiveTranscode && streamStartMs == 0L) restartStreamAt(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex == currentMediaItemIndex) seekTo(positionMs) else super.seekTo(mediaItemIndex, positionMs)
    }

    override fun seekBack() = seekTo((currentPosition - seekBackIncrement).coerceAtLeast(0L))

    override fun seekForward() {
        val target = currentPosition + seekForwardIncrement
        seekTo(duration.takeIf { it != C.TIME_UNSET }?.let(target::coerceAtMost) ?: target)
    }

    /** Puts the tracks not playing that were started part way back to their start. */
    private fun restoreLeftTracks() {
        for (index in 0 until mediaItemCount) {
            val item = getMediaItemAt(index)
            val uri = item.localConfiguration?.uri
            if (index != currentMediaItemIndex && uri?.getQueryParameter(START_PARAM) != null) {
                replaceMediaItem(index, item.buildUpon().setUri(uri.withStart(null)).build())
            }
        }
    }

    /** Plays the track's stream again from [positionMs], as playing or paused as it was. */
    private fun restartStreamAt(positionMs: Long) {
        val item = currentMediaItem ?: return
        val uri = item.localConfiguration?.uri ?: return
        val restarted = item.buildUpon().setUri(uri.withStart(positionMs)).build()
        replaceMediaItem(currentMediaItemIndex, restarted)
    }

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

        /** Jellyfin's stream parameter for where in the item a transcode starts. */
        const val START_PARAM = "StartTimeTicks"
        internal const val TICKS_PER_MS = 10_000L
    }
}

/** This item, its stream from the beginning. */
private fun MediaItem.fromItsStart(): MediaItem {
    val uri = localConfiguration?.uri ?: return this
    if (uri.getQueryParameter(AudiobookPlayer.START_PARAM) == null) return this
    return buildUpon().setUri(uri.withStart(null)).build()
}

/** Where this item's stream starts in its track: past 0 when it's a transcode started part way. */
val MediaItem.streamStartMs: Long
    get() = localConfiguration?.uri?.getQueryParameter(AudiobookPlayer.START_PARAM)
        ?.toLongOrNull()?.div(AudiobookPlayer.TICKS_PER_MS) ?: 0L

/** This stream, starting from [positionMs]; from the beginning when that's null. */
private fun Uri.withStart(positionMs: Long?): Uri {
    val builder = buildUpon().clearQuery()
    for (name in queryParameterNames) {
        if (name.equals(AudiobookPlayer.START_PARAM, ignoreCase = true)) continue
        for (value in getQueryParameters(name)) builder.appendQueryParameter(name, value)
    }
    if (positionMs != null) {
        builder.appendQueryParameter(
            AudiobookPlayer.START_PARAM,
            (positionMs * AudiobookPlayer.TICKS_PER_MS).toString(),
        )
    }
    return builder.build()
}
