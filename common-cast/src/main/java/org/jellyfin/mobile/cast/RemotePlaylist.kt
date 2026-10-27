package org.jellyfin.mobile.cast

import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import kotlin.math.max
import kotlin.math.min

/**
 * The cast player, with its queue kept as it was sent. Media3's cast player reads its items back
 * from the cast device, which knows only some of them (the rest come back as MediaItem.EMPTY, with
 * no uri) and none once its session ends, so moving playback back to this device takes them from
 * [items]. Every change to the playlist goes through here (the session's player is this), and is
 * made to [items] the way the player makes it, out-of-range indexes clamped alike.
 *
 * Asked for the queue, which song is playing, or a song or its metadata, it answers from [items]
 * too, and so does every event it passes on (see [SentQueueListener]), so what reads them through
 * the session (chapters and progress in an app's own player, the notification) gets the songs as
 * sent. Only while its copy and the player agree on the queue's length; otherwise the player's own
 * answer stands.
 *
 * The Cast channel caps a message's size, so a long queue goes [SONGS_PER_MESSAGE] songs at a time:
 * the song to play and those after it first, then the rest as the cast device reports each part (it
 * drops songs added before it has reported the queue). Meanwhile this answers for the whole queue,
 * so indexes mean the same as they will once it's all there: a seek to a song not sent yet sends the
 * queue again from that song, and changes to it (a burst of them, as an app makes around the song
 * playing) send it again, once, from the song playing. If the cast device's queue stops matching
 * what was sent (another sender changed it, or it dropped a song), it's sent again; after
 * [MAX_RESENDS] the cast device's own answers stand.
 */
// One override per playlist call Player has: each must be mirrored
@Suppress("TooManyFunctions")
@UnstableApi
internal class RemotePlaylist(
    player: Player,
    /** How long a change waits for the rest of its burst before the queue is sent again. */
    private val resendAfterMs: Long = RESEND_AFTER_MS,
) : ForwardingPlayer(player) {

    private val tracked = mutableListOf<MediaItem>()

    /** Parts of a long queue not sent yet: those to add before what's sent (nearest first), and after. */
    private val unsentBefore = ArrayDeque<List<MediaItem>>()
    private val unsentAfter = ArrayDeque<List<MediaItem>>()

    /** Where the cast device's queue starts in [tracked], and how long it is, once what's been sent arrives. */
    private var sentFrom = 0
    private var sentCount = 0

    /** The part last sent that the cast device hasn't reported yet: how many songs, and whether in front. */
    private var arriving = 0
    private var arrivingInFront = false

    /** Whether the cast device has reported any of the queue since it was last sent. */
    private var started = false

    /** The song a long queue was sent to start at, answered until the cast device reports it. */
    private var startIndex = 0

    /** Times the queue was sent again because the cast device's stopped matching it. */
    private var resends = 0

    /** Whether the cast device's queue stopped matching too often: its own answers stand. */
    private var gaveUp = false

    /** A change waiting to be sent, with the song playing (and where) as the burst of changes began. */
    private var pendingResend: Resend? = null

    private class Resend(val playing: MediaItem?, val index: Int, val positionMs: Long)

    private val handler = Handler(player.applicationLooper)
    private val resend = Runnable(::resendNow)

    /** Whether the cast device holds only part of [tracked]: a long queue still on its way. */
    private val sending
        get() = !gaveUp &&
            (pendingResend != null || arriving > 0 || unsentBefore.isNotEmpty() || unsentAfter.isNotEmpty())

    /**
     * Where the cast device's queue starts in [tracked] now. A part sent in front moves it, but only
     * once the cast device has it (Media3's cast player waits for the device to say; others don't).
     */
    private val deviceFrom
        get() = if (arrivingInFront && arriving > 0 && super.getMediaItemCount() != sentCount) {
            sentFrom + arriving
        } else {
            sentFrom
        }

    init {
        // Each part of a long queue goes once the cast device has reported the last
        wrappedPlayer.addListener(
            object : Player.Listener {
                override fun onTimelineChanged(timeline: Timeline, reason: Int) = onDeviceQueue(timeline.windowCount)
            },
        )
    }

    /** The queue as sent, in the player's order, all of it even while a long one is on its way. */
    val items: List<MediaItem> get() = tracked.toList()

    /** The song at [index] as sent, while [items] and the player agree on the queue. */
    private fun sentAt(index: Int): MediaItem? = when {
        gaveUp -> null
        sending -> tracked.getOrNull(index)
        else -> tracked.getOrNull(index)?.takeIf { sentCount == super.getMediaItemCount() }
    }

    override fun getCurrentMediaItem(): MediaItem? = sentAt(currentMediaItemIndex) ?: super.getCurrentMediaItem()

    override fun getMediaItemAt(index: Int): MediaItem = sentAt(index) ?: super.getMediaItemAt(index)

    override fun getMediaMetadata(): MediaMetadata =
        sentAt(currentMediaItemIndex)?.mediaMetadata ?: super.getMediaMetadata()

    override fun getMediaItemCount(): Int = if (sending) tracked.size else super.getMediaItemCount()

    override fun getCurrentMediaItemIndex(): Int {
        if (!sending) return super.getCurrentMediaItemIndex()
        pendingResend?.let { return it.indexNow() }
        if (!started) return startIndex
        return (deviceFrom + super.getCurrentMediaItemIndex()).coerceIn(0, max(0, tracked.lastIndex))
    }

    override fun getCurrentPeriodIndex(): Int = if (sending) currentMediaItemIndex else super.getCurrentPeriodIndex()

    override fun getCurrentTimeline(): Timeline = reported(super.getCurrentTimeline())

    override fun getNextMediaItemIndex(): Int =
        if (sending) {
            queueTimeline().getNextWindowIndex(currentMediaItemIndex, navigationRepeatMode, shuffleModeEnabled)
        } else {
            super.getNextMediaItemIndex()
        }

    override fun getPreviousMediaItemIndex(): Int =
        if (sending) {
            queueTimeline().getPreviousWindowIndex(
                currentMediaItemIndex,
                navigationRepeatMode,
                shuffleModeEnabled,
            )
        } else {
            super.getPreviousMediaItemIndex()
        }

    override fun hasNextMediaItem(): Boolean = nextMediaItemIndex != C.INDEX_UNSET

    override fun hasPreviousMediaItem(): Boolean = previousMediaItemIndex != C.INDEX_UNSET

    /** Skipping a song steps over it even when repeating one, as a player does. */
    private val navigationRepeatMode
        get() = if (repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else repeatMode

    override fun addListener(listener: Player.Listener) =
        super.addListener(SentQueueListener(listener, ::reported, ::reported) { sentAt(currentMediaItemIndex) })

    override fun removeListener(listener: Player.Listener) =
        super.removeListener(SentQueueListener(listener, ::reported, ::reported) { null })

    /**
     * [timeline] with each window's item as sent, while it and [items] agree on the queue's length;
     * the whole queue while a long one is on its way.
     */
    private fun reported(timeline: Timeline): Timeline = when {
        gaveUp -> timeline
        sending -> queueTimeline()
        timeline.windowCount != sentCount || timeline.isEmpty -> timeline
        else -> SentTimeline(timeline, items)
    }

    /** [position] (the cast device's) in the whole queue, while a long one is on its way. */
    private fun reported(position: Player.PositionInfo): Player.PositionInfo {
        if (!sending) return position
        val index = (deviceFrom + position.mediaItemIndex).coerceIn(0, max(0, tracked.lastIndex))
        return Player.PositionInfo(
            index,
            index,
            tracked.getOrNull(index),
            index,
            index,
            position.positionMs,
            position.contentPositionMs,
            position.adGroupIndex,
            position.adIndexInAdGroup,
        )
    }

    private fun queueTimeline(): Timeline = QueueTimeline(tracked.toList(), currentMediaItemIndex, super.getDuration())

    override fun seekToDefaultPosition(mediaItemIndex: Int) = seekTo(mediaItemIndex, C.TIME_UNSET)

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (!sending) return super.seekTo(mediaItemIndex, positionMs)
        val onDevice = mediaItemIndex - deviceFrom
        if (pendingResend == null && started && onDevice in 0 until super.getMediaItemCount()) {
            return super.seekTo(onDevice, positionMs)
        }
        // Not on the cast device yet: the queue goes again, from there
        if (tracked.isEmpty()) return
        send(mediaItemIndex.coerceIn(0, tracked.lastIndex), positionMs)
    }

    override fun seekToNextMediaItem() {
        if (!sending) return super.seekToNextMediaItem()
        nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { seekTo(it, C.TIME_UNSET) }
    }

    override fun seekToPreviousMediaItem() {
        if (!sending) return super.seekToPreviousMediaItem()
        previousMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { seekTo(it, C.TIME_UNSET) }
    }

    override fun seekToNext() = if (sending) seekToNextMediaItem() else super.seekToNext()

    override fun seekToPrevious() {
        if (!sending) return super.seekToPrevious()
        if (hasPreviousMediaItem() && currentPosition <= maxSeekToPreviousPosition) {
            seekToPreviousMediaItem()
        } else {
            seekTo(0L)
        }
    }

    override fun setMediaItem(mediaItem: MediaItem) = setMediaItems(listOf(mediaItem))

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) =
        setMediaItems(listOf(mediaItem), 0, startPositionMs)

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) =
        setMediaItems(listOf(mediaItem), resetPosition)

    override fun setMediaItems(mediaItems: List<MediaItem>) = setMediaItems(mediaItems, true)

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        if (resetPosition) {
            setMediaItems(mediaItems, 0, C.TIME_UNSET)
        } else {
            setMediaItems(mediaItems, currentMediaItemIndex.coerceIn(0, max(0, mediaItems.lastIndex)), currentPosition)
        }
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        tracked.clear()
        tracked.addAll(mediaItems)
        resends = 0
        send(startIndex, startPositionMs)
    }

    override fun addMediaItem(mediaItem: MediaItem) = addMediaItems(listOf(mediaItem))

    override fun addMediaItem(index: Int, mediaItem: MediaItem) = addMediaItems(index, listOf(mediaItem))

    override fun addMediaItems(mediaItems: List<MediaItem>) =
        edit(mediaItems.size, { add(tracked.size, mediaItems) }) { super.addMediaItems(mediaItems) }

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) =
        edit(mediaItems.size, { add(index, mediaItems) }) { super.addMediaItems(index, mediaItems) }

    override fun moveMediaItem(currentIndex: Int, newIndex: Int) =
        moveMediaItems(currentIndex, currentIndex + 1, newIndex)

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) =
        edit(0, { move(fromIndex, toIndex, newIndex) }) { super.moveMediaItems(fromIndex, toIndex, newIndex) }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) =
        replaceMediaItems(index, index + 1, listOf(mediaItem))

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) =
        edit(mediaItems.size, { replace(fromIndex, toIndex, mediaItems) }) {
            super.replaceMediaItems(fromIndex, toIndex, mediaItems)
        }

    override fun removeMediaItem(index: Int) = removeMediaItems(index, index + 1)

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) =
        edit(0, { remove(fromIndex, toIndex) }) { super.removeMediaItems(fromIndex, toIndex) }

    override fun clearMediaItems() {
        tracked.clear()
        reset()
        sentCount = 0
        super.clearMediaItems()
    }

    override fun stop() {
        // Left behind (playback moved to this device): nothing more goes to the cast device
        handler.removeCallbacks(resend)
        super.stop()
    }

    /**
     * Makes a change to the queue: to [tracked] by [mirror], and to the cast device by [forward].
     * While a long queue is still on its way, or the change adds more songs than one message holds,
     * the queue is sent again instead, from the song playing: once for a burst of changes.
     */
    private inline fun edit(adding: Int, mirror: () -> Unit, forward: () -> Unit) {
        if (gaveUp || (!sending && adding <= SONGS_PER_MESSAGE)) {
            mirror()
            forward()
            if (!gaveUp) sentCount = tracked.size
            return
        }
        if (pendingResend == null) {
            val index = currentMediaItemIndex
            pendingResend = Resend(tracked.getOrNull(index), index, currentPosition)
            handler.postDelayed(resend, resendAfterMs)
        }
        mirror()
    }

    /** Where the song playing as a burst of changes began is now; next in line if it went. */
    private fun Resend.indexNow(): Int =
        tracked.indexOfFirst { it === playing }.takeIf { it >= 0 } ?: index.coerceIn(0, max(0, tracked.lastIndex))

    private fun resendNow() {
        val change = pendingResend ?: return
        pendingResend = null
        if (tracked.isEmpty()) {
            clearMediaItems()
            return
        }
        val stillPlaying = tracked.any { it === change.playing }
        send(change.indexNow(), if (stillPlaying) change.positionMs else C.TIME_UNSET)
    }

    /** Sends [tracked] to play from [startIndex]: a long queue a part at a time (see [sendNextPart]). */
    private fun send(startIndex: Int, startPositionMs: Long) {
        reset()
        val queue = tracked.toList()
        if (queue.size <= SONGS_PER_MESSAGE) {
            sentCount = queue.size
            super.setMediaItems(queue, startIndex, startPositionMs)
            return
        }
        val from = startIndex.coerceIn(0, queue.lastIndex)
        val to = min(from + SONGS_PER_MESSAGE, queue.size)
        for (end in from downTo 1 step SONGS_PER_MESSAGE) {
            unsentBefore.addLast(queue.subList(max(0, end - SONGS_PER_MESSAGE), end))
        }
        queue.subList(to, queue.size).chunked(SONGS_PER_MESSAGE).forEach(unsentAfter::addLast)
        sentFrom = from
        sentCount = to - from
        arriving = sentCount
        this.startIndex = from
        super.setMediaItems(queue.subList(from, to), 0, startPositionMs)
    }

    /** The cast device reported a queue of [count] songs: a part has arrived, or it no longer matches. */
    private fun onDeviceQueue(count: Int) {
        if (!sending || pendingResend != null) return
        when {
            count == sentCount -> {
                started = true
                arriving = 0
                sendNextPart()
            }
            // Still on its way (or, before any has arrived, the queue from before)
            count == sentCount - arriving || !started -> Unit
            resends < MAX_RESENDS -> {
                resends++
                val index = currentMediaItemIndex
                pendingResend = Resend(tracked.getOrNull(index), index, currentPosition)
                handler.post(resend)
            }
            else -> {
                reset()
                gaveUp = true
            }
        }
    }

    /** The next part of a long queue: those before the song playing first, so its place is soon right. */
    private fun sendNextPart() {
        val before = unsentBefore.removeFirstOrNull()
        if (before != null) {
            sentFrom -= before.size
            sentCount += before.size
            arriving = before.size
            arrivingInFront = true
            super.addMediaItems(0, before)
            return
        }
        val after = unsentAfter.removeFirstOrNull() ?: return
        sentCount += after.size
        arriving = after.size
        arrivingInFront = false
        super.addMediaItems(after)
    }

    /** Nothing on its way: what's sent is all there is, from the start of [tracked]. */
    private fun reset() {
        handler.removeCallbacks(resend)
        pendingResend = null
        unsentBefore.clear()
        unsentAfter.clear()
        sentFrom = 0
        arriving = 0
        arrivingInFront = false
        started = false
        gaveUp = false
    }

    private fun add(index: Int, items: List<MediaItem>) {
        tracked.addAll(min(index, tracked.size), items)
    }

    private fun remove(fromIndex: Int, toIndex: Int) {
        val to = min(toIndex, tracked.size)
        if (fromIndex >= to) return
        tracked.subList(fromIndex, to).clear()
    }

    private fun move(fromIndex: Int, toIndex: Int, newIndex: Int) {
        val to = min(toIndex, tracked.size)
        if (fromIndex >= to) return
        val moved = tracked.subList(fromIndex, to).toList()
        tracked.subList(fromIndex, to).clear()
        tracked.addAll(min(newIndex, tracked.size), moved)
    }

    private fun replace(fromIndex: Int, toIndex: Int, items: List<MediaItem>) {
        if (fromIndex > tracked.size) return
        remove(fromIndex, toIndex)
        tracked.addAll(fromIndex, items)
    }
}

/** [timeline] as it is, except each window holds the item as sent. */
@UnstableApi
private class SentTimeline(private val timeline: Timeline, private val items: List<MediaItem>) : Timeline() {
    override fun getWindowCount(): Int = timeline.windowCount

    override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window =
        timeline.getWindow(windowIndex, window, defaultPositionProjectionUs).apply { mediaItem = items[windowIndex] }

    override fun getNextWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int =
        timeline.getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)

    override fun getPreviousWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean): Int =
        timeline.getPreviousWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)

    override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int = timeline.getFirstWindowIndex(
        shuffleModeEnabled,
    )

    override fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int = timeline.getLastWindowIndex(shuffleModeEnabled)

    override fun getPeriodCount(): Int = timeline.periodCount

    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period =
        timeline.getPeriod(periodIndex, period, setIds)

    override fun getIndexOfPeriod(uid: Any): Int = timeline.getIndexOfPeriod(uid)

    override fun getUidOfPeriod(periodIndex: Int): Any = timeline.getUidOfPeriod(periodIndex)
}

/**
 * The whole of a long queue while it's on its way to the cast device: one window (and period) per
 * song, its uid its index, lasting as long as its metadata says (the song playing, as long as the
 * player says, [currentDurationMs]).
 */
@UnstableApi
private class QueueTimeline(
    private val items: List<MediaItem>,
    private val currentIndex: Int,
    private val currentDurationMs: Long,
) : Timeline() {
    override fun getWindowCount(): Int = items.size

    override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window =
        window.set(
            windowIndex,
            items[windowIndex],
            null,
            C.TIME_UNSET,
            C.TIME_UNSET,
            C.TIME_UNSET,
            true,
            false,
            null,
            0L,
            durationUs(windowIndex),
            windowIndex,
            windowIndex,
            0L,
        )

    override fun getPeriodCount(): Int = items.size

    override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period =
        period.set(periodIndex, periodIndex, periodIndex, durationUs(periodIndex), 0L)

    override fun getIndexOfPeriod(uid: Any): Int = (uid as? Int)?.takeIf { it in items.indices } ?: C.INDEX_UNSET

    override fun getUidOfPeriod(periodIndex: Int): Any = periodIndex

    private fun durationUs(index: Int): Long {
        val durationMs = if (index == currentIndex && currentDurationMs != C.TIME_UNSET) {
            currentDurationMs
        } else {
            items[index].mediaMetadata.durationMs ?: C.TIME_UNSET
        }
        return Util.msToUs(durationMs)
    }
}

/**
 * Most songs sent to a cast device in one message: the Cast channel caps a message's size, and each
 * song carries its stream url and metadata.
 */
internal const val SONGS_PER_MESSAGE = 20

/** Times a long queue is sent again when the cast device's stops matching it, before its own answers stand. */
internal const val MAX_RESENDS = 2

/** Long enough for an app's burst of queue changes (each its own message to the session) to arrive. */
internal const val RESEND_AFTER_MS = 200L
