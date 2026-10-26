package org.jellyfin.mobile.cast

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
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
 * drops songs added before it has reported the queue). Until then the queue reads as the part it
 * has, and a change to the queue sends it all again from the song playing.
 */
// One override per playlist call Player has: each must be mirrored
@Suppress("TooManyFunctions")
@UnstableApi
internal class RemotePlaylist(player: Player) : ForwardingPlayer(player) {

    private val tracked = mutableListOf<MediaItem>()

    /** Parts of a long queue not sent yet: those to add before what's sent (nearest first), and after. */
    private val unsentBefore = ArrayDeque<List<MediaItem>>()
    private val unsentAfter = ArrayDeque<List<MediaItem>>()

    /** How many songs the cast device holds once what's been sent arrives. */
    private var sentCount = 0

    private val sending get() = unsentBefore.isNotEmpty() || unsentAfter.isNotEmpty()

    /** Where what's sent starts in [tracked]: after the songs still to add before it. */
    private val sentFrom get() = unsentBefore.sumOf { it.size }

    init {
        // Each part of a long queue goes once the cast device has reported the last
        wrappedPlayer.addListener(
            object : Player.Listener {
                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    if (sending && timeline.windowCount == sentCount) sendNextPart()
                }
            },
        )
    }

    /** The queue as sent, in the player's order: while a long one is being sent, the part it has. */
    val items: List<MediaItem> get() = tracked.subList(sentFrom, sentFrom + sentCount).toList()

    /** The song at [index] as sent, while [items] and the player agree on the queue. */
    private fun sentAt(index: Int): MediaItem? =
        tracked.getOrNull(sentFrom + index)?.takeIf { index < sentCount && sentCount == mediaItemCount }

    override fun getCurrentMediaItem(): MediaItem? = sentAt(currentMediaItemIndex) ?: super.getCurrentMediaItem()

    override fun getMediaItemAt(index: Int): MediaItem = sentAt(index) ?: super.getMediaItemAt(index)

    override fun getMediaMetadata(): MediaMetadata =
        sentAt(currentMediaItemIndex)?.mediaMetadata ?: super.getMediaMetadata()

    override fun getCurrentTimeline(): Timeline = sent(super.getCurrentTimeline())

    override fun addListener(listener: Player.Listener) =
        super.addListener(SentQueueListener(listener, ::sent) { sentAt(currentMediaItemIndex) })

    override fun removeListener(listener: Player.Listener) =
        super.removeListener(SentQueueListener(listener, ::sent) { null })

    /** [timeline] with each window's item as sent, while it and [items] agree on the queue's length. */
    private fun sent(timeline: Timeline): Timeline {
        if (timeline.windowCount != sentCount || timeline.isEmpty) return timeline
        return SentTimeline(timeline, items)
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
        send(startIndex, startPositionMs)
    }

    override fun addMediaItem(mediaItem: MediaItem) = addMediaItems(listOf(mediaItem))

    override fun addMediaItem(index: Int, mediaItem: MediaItem) = addMediaItems(index, listOf(mediaItem))

    override fun addMediaItems(mediaItems: List<MediaItem>) =
        edit({ add(tracked.size, mediaItems) }) { super.addMediaItems(mediaItems) }

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) =
        edit({ add(index, mediaItems) }) { super.addMediaItems(index, mediaItems) }

    override fun moveMediaItem(currentIndex: Int, newIndex: Int) =
        moveMediaItems(currentIndex, currentIndex + 1, newIndex)

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) =
        edit({ move(fromIndex, toIndex, newIndex) }) { super.moveMediaItems(fromIndex, toIndex, newIndex) }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) =
        replaceMediaItems(index, index + 1, listOf(mediaItem))

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) =
        edit({ replace(fromIndex, toIndex, mediaItems) }) { super.replaceMediaItems(fromIndex, toIndex, mediaItems) }

    override fun removeMediaItem(index: Int) = removeMediaItems(index, index + 1)

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) =
        edit({ remove(fromIndex, toIndex) }) { super.removeMediaItems(fromIndex, toIndex) }

    override fun clearMediaItems() {
        tracked.clear()
        stopSending()
        sentCount = 0
        super.clearMediaItems()
    }

    /**
     * Makes a change to the queue: to [tracked] by [mirror], and to the cast device by [forward].
     * While a long queue is still being sent, the cast device doesn't hold it all, so instead it's
     * sent again, changed, from the song playing.
     */
    private inline fun edit(mirror: () -> Unit, forward: () -> Unit) {
        if (!sending) {
            mirror()
            forward()
            sentCount = tracked.size
            return
        }
        val playing = tracked.getOrNull(sentFrom + super.getCurrentMediaItemIndex())
        val position = super.getCurrentPosition()
        mirror()
        send(tracked.indexOfFirst { it === playing }.coerceAtLeast(0), position)
    }

    /** Sends [tracked] to play from [startIndex]: a long queue a part at a time (see [sendNextPart]). */
    private fun send(startIndex: Int, startPositionMs: Long) {
        stopSending()
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
        sentCount = to - from
        super.setMediaItems(queue.subList(from, to), 0, startPositionMs)
    }

    /** The next part of a long queue: those before the song playing first, so its place is soon right. */
    private fun sendNextPart() {
        val before = unsentBefore.removeFirstOrNull()
        if (before != null) {
            sentCount += before.size
            super.addMediaItems(0, before)
            return
        }
        val after = unsentAfter.removeFirstOrNull() ?: return
        sentCount += after.size
        super.addMediaItems(after)
    }

    private fun stopSending() {
        unsentBefore.clear()
        unsentAfter.clear()
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
 * Most songs sent to a cast device in one message: the Cast channel caps a message's size, and each
 * song carries its stream url and metadata.
 */
internal const val SONGS_PER_MESSAGE = 20
