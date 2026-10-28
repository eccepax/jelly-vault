package org.jellyfin.mobile.player.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline

/**
 * This device's player, seeking in transcodes. A transcode is a live stream the player can't seek
 * in, so seeking in one starts the stream again from the target (the server transcodes from
 * [START_PARAM]). Positions and the length still count from the start of the whole track, for
 * everything that reads them. A track started part way is put back to its start once it's left, so
 * going back to it later plays it from its beginning.
 *
 * Whether a stream is a transcode is only known once it's open: a place asked for before then (a
 * queue set from part way, a seek while it loads) is where a transcode starts once it turns out to
 * be one. That place is kept with its track, not its index, so tracks added or moved meanwhile
 * don't lose it. A queue set, added to or replaced in, however it's done, starts its streams from
 * their beginnings, whatever their uris said; the track playing, replaced with itself (new details),
 * keeps the stream it plays from.
 *
 * Seeking is offered whenever something is loaded, as the player's own commands don't once a
 * transcode has opened.
 *
 * Only for this device's player: a cast player starts and seeks its own streams and counts from the
 * track's start (see common-cast's CastQueuePlayer). [AudiobookPlayer] goes on top for the chapters.
 */
class TranscodeSeekingPlayer(player: Player) : OwnCommandsPlayer(player) {

    /** A place asked for (the track, by its id, and where in it) before its stream was open. */
    private var pendingStart: Pair<String, Long>? = null

    /**
     * What's known of the playing item's stream, for the item and timeline it was read from: where
     * it starts in its track, whether it's open yet, and whether it's a transcode. Read once for
     * each, rather than on every position, length and command read.
     */
    private class Stream(
        val item: MediaItem?,
        val timeline: Timeline,
        val index: Int,
        val startMs: Long,
        val isOpening: Boolean,
        val isLiveTranscode: Boolean,
    )

    private var stream: Stream? = null
    private val window = Timeline.Window()

    init {
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = restoreLeftTracks()

                override fun onTimelineChanged(timeline: Timeline, reason: Int) = startPending()
            },
        )
    }

    private val playing: Stream
        get() {
            val item = currentMediaItem
            val timeline = currentTimeline
            val index = currentMediaItemIndex
            stream?.takeIf { it.item === item && it.timeline === timeline && it.index == index }?.let { return it }
            val startMs = item?.streamStartMs ?: 0L
            val opening = index >= timeline.windowCount || timeline.getWindow(index, window).isPlaceholder
            return Stream(item, timeline, index, startMs, opening, startMs > 0 || isKnownTranscode(index))
                .also { stream = it }
        }

    /** Where the playing stream starts in its track: past 0 when a transcode was started part way. */
    private val streamStartMs: Long get() = playing.startMs

    /**
     * A transcode: one the player has opened and can't seek in, or one already started part way,
     * which only a transcode is (it's still loading, so the player doesn't know yet).
     */
    private val isLiveTranscode: Boolean get() = playing.isLiveTranscode

    /** Whether the playing item's stream isn't open yet, so whether it can seek isn't known. */
    private val isOpening: Boolean get() = playing.isOpening

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

    override fun isCommandAvailable(command: Int): Boolean = command in seekCommands || super.isCommandAvailable(command)

    override fun getAvailableCommands(): Player.Commands {
        val commands = super.getAvailableCommands()
        return if (seekCommands.isEmpty()) commands else commands.buildUpon().addAll(*seekCommands).build()
    }

    /** Seeking, whenever something is loaded: in a transcode, it starts the stream again from the place. */
    private val seekCommands: IntArray get() = if (mediaItemCount == 0) IntArray(0) else SEEK_COMMANDS

    override fun seekTo(positionMs: Long) {
        if (isLiveTranscode) return restartStreamAt(positionMs)
        if (isOpening) pendingStart = currentMediaItem?.let { it.mediaId to positionMs }
        super.seekTo(positionMs - streamStartMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex == currentMediaItemIndex) return seekTo(positionMs)
        if (mediaItemIndex !in 0 until mediaItemCount) return super.seekTo(mediaItemIndex, positionMs)
        // A transcode can't seek: it's started again from the place, now if it's known to be one, else once it opens
        pendingStart = getMediaItemAt(mediaItemIndex).mediaId.takeIf { positionMs > 0 }?.let { it to positionMs }
        super.seekTo(mediaItemIndex, if (isKnownTranscode(mediaItemIndex)) 0L else positionMs)
        startPending()
    }

    /** Whether the item at [index] has its stream open, and can't seek in it. */
    private fun isKnownTranscode(index: Int): Boolean {
        val timeline = currentTimeline
        if (index >= timeline.windowCount) return false
        timeline.getWindow(index, window)
        return !window.isPlaceholder && !window.isSeekable
    }

    // ForwardingPlayer sends a seek back or forward straight to the player, which can't seek in a transcode
    override fun seekBack() = seekTo((currentPosition - seekBackIncrement).coerceAtLeast(0L))

    override fun seekForward() {
        val target = currentPosition + seekForwardIncrement
        seekTo(duration.takeIf { it != C.TIME_UNSET }?.let(target::coerceAtMost) ?: target)
    }

    // Every way of setting, adding to or replacing in the queue, since ForwardingPlayer sends each straight on

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        // Before the player has them: it tells its listeners of the new queue while they're being set
        pendingStart = mediaItems.getOrNull(startIndex)?.mediaId?.takeIf { startPositionMs > 0 }?.let { it to startPositionMs }
        super.setMediaItems(mediaItems.map { it.fromItsStart() }, startIndex, startPositionMs)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) = setMediaItems(mediaItems, true)

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        // Keeping the place is keeping the track's: the stream it's restarted from goes with the old item
        if (!resetPosition && currentMediaItemIndex in mediaItems.indices) {
            return setMediaItems(mediaItems, currentMediaItemIndex, currentPosition)
        }
        pendingStart = null
        super.setMediaItems(mediaItems.map { it.fromItsStart() }, resetPosition)
    }

    override fun setMediaItem(mediaItem: MediaItem) = setMediaItems(listOf(mediaItem))

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) =
        setMediaItems(listOf(mediaItem), 0, startPositionMs)

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) =
        setMediaItems(listOf(mediaItem), resetPosition)

    override fun addMediaItems(mediaItems: List<MediaItem>) = super.addMediaItems(mediaItems.map { it.fromItsStart() })

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) =
        super.addMediaItems(index, mediaItems.map { it.fromItsStart() })

    override fun addMediaItem(mediaItem: MediaItem) = addMediaItems(listOf(mediaItem))

    override fun addMediaItem(index: Int, mediaItem: MediaItem) = addMediaItems(index, listOf(mediaItem))

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) = replaceMediaItems(
        index,
        index + 1,
        listOf(mediaItem),
    )

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) {
        val playing = currentMediaItem
        val playingIndex = currentMediaItemIndex
        val startMs = streamStartMs
        val items = mediaItems.mapIndexed { i, item ->
            val samePlaying = fromIndex + i == playingIndex && fromIndex + i < toIndex && item.mediaId == playing?.mediaId
            if (samePlaying && startMs > 0) item.startingAt(startMs) else item.fromItsStart()
        }
        super.replaceMediaItems(fromIndex, toIndex, items)
    }

    /**
     * The stream asked for part way is open: a transcode, which played from its start, starts there
     * instead; unless that's its start anyway (a seek back to it), which would only start the
     * server's transcode again for nothing.
     */
    private fun startPending() {
        val (mediaId, positionMs) = pendingStart ?: return
        if (currentMediaItem?.mediaId != mediaId) {
            pendingStart = null
            return
        }
        if (isOpening) return
        pendingStart = null
        if (isLiveTranscode && streamStartMs == 0L && positionMs > AT_ITS_START_MS) restartStreamAt(positionMs)
    }

    /** Puts the tracks not playing that were started part way back to their start. */
    private fun restoreLeftTracks() {
        for (index in 0 until mediaItemCount) {
            if (index == currentMediaItemIndex) continue
            val item = getMediaItemAt(index)
            val fromStart = item.fromItsStart()
            if (fromStart !== item) super.replaceMediaItem(index, fromStart)
        }
    }

    /** Plays the track's stream again from [positionMs], as playing or paused as it was. */
    private fun restartStreamAt(positionMs: Long) {
        val item = currentMediaItem ?: return
        val uri = item.localConfiguration?.uri ?: return
        val restarted = item.buildUpon().setUri(uri.withStart(positionMs)).build()
        super.replaceMediaItem(currentMediaItemIndex, restarted)
    }

    companion object {
        /** Jellyfin's stream parameter for where in the item a transcode starts. */
        const val START_PARAM = "StartTimeTicks"
        private const val TICKS_PER_MS = 10_000L

        private val SEEK_COMMANDS = intArrayOf(
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
        )

        /** Close enough to a track's start that starting its transcode again there gains nothing. */
        private const val AT_ITS_START_MS = 1_000L

        /** Where this item's stream starts in its track: past 0 when it's a transcode started part way. */
        private val MediaItem.streamStartMs: Long
            get() = localConfiguration?.uri?.getQueryParameter(START_PARAM)?.toLongOrNull()?.div(TICKS_PER_MS) ?: 0L

        /**
         * [uri] counting from its track's start, whatever this device's stream was restarted from: for
         * a cast device, which asks for its own streams from where it should start.
         */
        fun fromTrackStart(uri: Uri): Uri = if (uri.getQueryParameter(START_PARAM) == null) uri else uri.withStart(null)

        /** This item, its stream from the beginning; itself when that's how it is already. */
        private fun MediaItem.fromItsStart(): MediaItem {
            val uri = localConfiguration?.uri ?: return this
            if (uri.getQueryParameter(START_PARAM) == null) return this
            return buildUpon().setUri(uri.withStart(null)).build()
        }

        /** This item, its stream starting [positionMs] into its track. */
        private fun MediaItem.startingAt(positionMs: Long): MediaItem {
            val uri = localConfiguration?.uri ?: return this
            return buildUpon().setUri(uri.withStart(positionMs)).build()
        }

        /** This stream, starting from [positionMs]; from the beginning when that's null. */
        private fun Uri.withStart(positionMs: Long?): Uri {
            val builder = buildUpon().clearQuery()
            for (name in queryParameterNames) {
                if (name.equals(START_PARAM, ignoreCase = true)) continue
                for (value in getQueryParameters(name)) builder.appendQueryParameter(name, value)
            }
            if (positionMs != null) builder.appendQueryParameter(START_PARAM, (positionMs * TICKS_PER_MS).toString())
            return builder.build()
        }
    }
}
