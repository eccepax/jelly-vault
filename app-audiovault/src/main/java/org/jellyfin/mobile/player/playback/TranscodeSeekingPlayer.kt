package org.jellyfin.mobile.player.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
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
 * be one. A queue set or added to, however it's done, starts its streams from their beginnings,
 * whatever their uris said.
 *
 * Only for this device's player: a cast player starts and seeks its own streams and counts from the
 * track's start (see common-cast's CastQueuePlayer). [AudiobookPlayer] goes on top for the chapters.
 */
class TranscodeSeekingPlayer(player: Player) : ForwardingPlayer(player) {

    /** A place asked for (the item's index, and where in it) before its stream was open. */
    private var pendingStart: Pair<Int, Long>? = null

    init {
        player.addListener(
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = restoreLeftTracks()

                override fun onTimelineChanged(timeline: Timeline, reason: Int) = startPending()
            },
        )
    }

    /** Where the playing stream starts in its track: past 0 when a transcode was started part way. */
    private val streamStartMs: Long get() = currentMediaItem?.streamStartMs ?: 0L

    /**
     * A transcode: one the player has opened and can't seek in, or one already started part way,
     * which only a transcode is (it's still loading, so the player doesn't know yet).
     */
    private val isLiveTranscode: Boolean
        get() {
            if (streamStartMs > 0) return true
            val timeline = currentTimeline
            if (timeline.isEmpty) return false
            val window = timeline.getWindow(currentMediaItemIndex, Timeline.Window())
            return !window.isPlaceholder && !window.isSeekable
        }

    /** Whether the playing item's stream isn't open yet, so whether it can seek isn't known. */
    private val isOpening: Boolean
        get() {
            val timeline = currentTimeline
            return timeline.isEmpty || timeline.getWindow(currentMediaItemIndex, Timeline.Window()).isPlaceholder
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
        if (isLiveTranscode) return restartStreamAt(positionMs)
        if (isOpening) pendingStart = currentMediaItemIndex to positionMs
        super.seekTo(positionMs - streamStartMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex == currentMediaItemIndex) seekTo(positionMs) else super.seekTo(mediaItemIndex, positionMs)
    }

    // ForwardingPlayer sends a seek back or forward straight to the player, which can't seek in a transcode
    override fun seekBack() = seekTo((currentPosition - seekBackIncrement).coerceAtLeast(0L))

    override fun seekForward() {
        val target = currentPosition + seekForwardIncrement
        seekTo(duration.takeIf { it != C.TIME_UNSET }?.let(target::coerceAtMost) ?: target)
    }

    // Every way of setting or adding to the queue, since ForwardingPlayer sends each straight on

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        super.setMediaItems(mediaItems.map { it.fromItsStart() }, startIndex, startPositionMs)
        pendingStart = (startIndex to startPositionMs).takeIf { startPositionMs > 0 }
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) = setMediaItems(mediaItems, true)

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
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

    /**
     * The stream asked for part way is open: a transcode, which played from its start, starts there
     * instead; unless that's its start anyway (a seek back to it), which would only start the
     * server's transcode again for nothing.
     */
    private fun startPending() {
        val (index, positionMs) = pendingStart ?: return
        if (currentMediaItemIndex != index) {
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
            if (fromStart !== item) replaceMediaItem(index, fromStart)
        }
    }

    /** Plays the track's stream again from [positionMs], as playing or paused as it was. */
    private fun restartStreamAt(positionMs: Long) {
        val item = currentMediaItem ?: return
        val uri = item.localConfiguration?.uri ?: return
        val restarted = item.buildUpon().setUri(uri.withStart(positionMs)).build()
        replaceMediaItem(currentMediaItemIndex, restarted)
    }

    companion object {
        /** Jellyfin's stream parameter for where in the item a transcode starts. */
        const val START_PARAM = "StartTimeTicks"
        private const val TICKS_PER_MS = 10_000L

        /** Close enough to a track's start that starting its transcode again there gains nothing. */
        private const val AT_ITS_START_MS = 1_000L

        /** Where this item's stream starts in its track: past 0 when it's a transcode started part way. */
        private val MediaItem.streamStartMs: Long
            get() = localConfiguration?.uri?.getQueryParameter(START_PARAM)?.toLongOrNull()?.div(TICKS_PER_MS) ?: 0L

        /** This item, its stream from the beginning; itself when that's how it is already. */
        private fun MediaItem.fromItsStart(): MediaItem {
            val uri = localConfiguration?.uri ?: return this
            if (uri.getQueryParameter(START_PARAM) == null) return this
            return buildUpon().setUri(uri.withStart(null)).build()
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
