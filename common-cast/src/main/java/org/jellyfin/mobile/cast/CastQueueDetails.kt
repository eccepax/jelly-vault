package org.jellyfin.mobile.cast

import androidx.annotation.OptIn
import androidx.media3.common.DeviceInfo
import androidx.media3.common.FlagSet
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi

/** The songs the app last sent cast devices, by media id, as it sent them. */
internal class SentItems(private val limit: Int = CAST_QUEUE_LIMIT * 2) {
    private val items = object : LinkedHashMap<String, MediaItem>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaItem>) = size > limit
    }

    fun remember(item: MediaItem) {
        if (item.mediaId.isEmpty()) return
        items.remove(item.mediaId)
        items[item.mediaId] = item
    }

    operator fun get(mediaId: String): MediaItem? = items[mediaId]
}

/** What the Cast framework knows of a cast device's queue beyond what Media3's cast player has. */
internal interface CastQueueLookup {
    /** The content id of the cast device's item [itemId]; null until it's fetched (asking fetches it). */
    fun contentIdOf(itemId: Int): String?

    /** [changed] is told whenever more of the queue is known. */
    fun onChanged(changed: () -> Unit)
}

/**
 * A cast device describes only the songs around the one playing; Media3's cast player has nothing
 * but the cast device's id for the rest. Those come back as the app [sent] them, looked up by that
 * id ([lookup]), and listeners hear of the queue again as more of it is known. Songs another sender
 * put there stay as the cast device has them. Played here, the player's own queue is left alone.
 */
@OptIn(UnstableApi::class)
internal class CastQueueDetails(
    player: Player,
    private val sent: SentItems,
    private val lookup: CastQueueLookup,
) : ForwardingPlayer(player) {
    /** The content id of each of the cast device's ids, once known: asked for once only. */
    private val contentIds = mutableMapOf<Int, String>()
    private val listeners = mutableListOf<Player.Listener>()

    init {
        lookup.onChanged(::moreKnown)
    }

    private val cast: Boolean get() = deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE

    override fun addListener(listener: Player.Listener) {
        super.addListener(listener)
        listeners += listener
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listener)
        listeners -= listener
    }

    override fun getCurrentTimeline(): Timeline {
        val timeline = super.getCurrentTimeline()
        return if (cast && !timeline.isEmpty) Filled(timeline) else timeline
    }

    override fun getMediaItemAt(index: Int): MediaItem =
        if (cast) currentTimeline.getWindow(index, Timeline.Window()).mediaItem else super.getMediaItemAt(index)

    override fun getCurrentMediaItem(): MediaItem? {
        if (!cast) return super.getCurrentMediaItem()
        val timeline = currentTimeline
        val index = currentMediaItemIndex
        return if (index in 0 until timeline.windowCount) {
            timeline.getWindow(
                index,
                Timeline.Window(),
            ).mediaItem
        } else {
            null
        }
    }

    /** [item] as the app sent it, when it's a bare id the cast device hasn't described. */
    private fun filled(itemId: Any?, item: MediaItem): MediaItem {
        sent[item.mediaId]?.let { return it }
        if (!item.bare || itemId !is Int) return item
        val contentId = contentIds[itemId] ?: lookup.contentIdOf(itemId)?.also { contentIds[itemId] = it }
        return contentId?.let { sent[it] } ?: item
    }

    /** More of the cast device's queue is known: listeners hear of the queue if any of it is new here. */
    private fun moreKnown() {
        if (!cast) return
        val timeline = super.getCurrentTimeline()
        val window = Timeline.Window()
        val newlyKnown = (0 until timeline.windowCount).count { i ->
            val itemId = timeline.getWindow(i, window).uid as? Int ?: return@count false
            window.mediaItem.bare && itemId !in contentIds &&
                lookup.contentIdOf(itemId)?.also { contentIds[itemId] = it } != null
        }
        if (newlyKnown == 0) return
        val filled = currentTimeline
        val events = Player.Events(FlagSet.Builder().add(Player.EVENT_TIMELINE_CHANGED).build())
        listeners.toList().forEach {
            it.onTimelineChanged(filled, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
            it.onEvents(this, events)
        }
    }

    /** [timeline] with each bare song as the app sent it. */
    private inner class Filled(private val timeline: Timeline) : Timeline() {
        override fun getWindowCount() = timeline.windowCount

        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
            timeline.getWindow(windowIndex, window, defaultPositionProjectionUs)
            window.mediaItem = filled(window.uid, window.mediaItem)
            return window
        }

        override fun getPeriodCount() = timeline.periodCount

        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period =
            timeline.getPeriod(periodIndex, period, setIds)

        override fun getIndexOfPeriod(uid: Any): Int = timeline.getIndexOfPeriod(uid)

        override fun getUidOfPeriod(periodIndex: Int): Any = timeline.getUidOfPeriod(periodIndex)

        override fun getNextWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean) =
            timeline.getNextWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)

        override fun getPreviousWindowIndex(windowIndex: Int, repeatMode: Int, shuffleModeEnabled: Boolean) =
            timeline.getPreviousWindowIndex(windowIndex, repeatMode, shuffleModeEnabled)

        override fun getFirstWindowIndex(shuffleModeEnabled: Boolean) = timeline.getFirstWindowIndex(shuffleModeEnabled)

        override fun getLastWindowIndex(shuffleModeEnabled: Boolean) = timeline.getLastWindowIndex(shuffleModeEnabled)
    }
}

/** A song the cast device gave only its id for (see VaultMediaItemConverter.toMediaItem). */
private val MediaItem.bare: Boolean get() = mediaId.isEmpty()
