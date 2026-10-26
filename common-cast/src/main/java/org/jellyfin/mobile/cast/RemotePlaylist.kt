package org.jellyfin.mobile.cast

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlin.math.min

/**
 * The cast player, with its queue kept as it was sent. Media3's cast player reads its items back
 * from the cast device, which knows only some of them (the rest come back as MediaItem.EMPTY, with
 * no uri) and none once its session ends, so moving playback back to this device takes them from
 * [items]. Every change to the playlist goes through here (the session's player is this), and is
 * made to [items] the way the player makes it, out-of-range indexes clamped alike.
 */
// One override per playlist call Player has: each must be mirrored
@Suppress("TooManyFunctions")
@UnstableApi
internal class RemotePlaylist(player: Player) : ForwardingPlayer(player) {

    private val tracked = mutableListOf<MediaItem>()

    /** The queue as sent, in the player's order. */
    val items: List<MediaItem> get() = tracked.toList()

    override fun setMediaItem(mediaItem: MediaItem) {
        replaceAll(listOf(mediaItem))
        super.setMediaItem(mediaItem)
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        replaceAll(listOf(mediaItem))
        super.setMediaItem(mediaItem, startPositionMs)
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        replaceAll(listOf(mediaItem))
        super.setMediaItem(mediaItem, resetPosition)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) {
        replaceAll(mediaItems)
        super.setMediaItems(mediaItems)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        replaceAll(mediaItems)
        super.setMediaItems(mediaItems, resetPosition)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        replaceAll(mediaItems)
        super.setMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun addMediaItem(mediaItem: MediaItem) {
        add(tracked.size, listOf(mediaItem))
        super.addMediaItem(mediaItem)
    }

    override fun addMediaItem(index: Int, mediaItem: MediaItem) {
        add(index, listOf(mediaItem))
        super.addMediaItem(index, mediaItem)
    }

    override fun addMediaItems(mediaItems: List<MediaItem>) {
        add(tracked.size, mediaItems)
        super.addMediaItems(mediaItems)
    }

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
        add(index, mediaItems)
        super.addMediaItems(index, mediaItems)
    }

    override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
        move(currentIndex, currentIndex + 1, newIndex)
        super.moveMediaItem(currentIndex, newIndex)
    }

    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {
        move(fromIndex, toIndex, newIndex)
        super.moveMediaItems(fromIndex, toIndex, newIndex)
    }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
        replace(index, index + 1, listOf(mediaItem))
        super.replaceMediaItem(index, mediaItem)
    }

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) {
        replace(fromIndex, toIndex, mediaItems)
        super.replaceMediaItems(fromIndex, toIndex, mediaItems)
    }

    override fun removeMediaItem(index: Int) {
        remove(index, index + 1)
        super.removeMediaItem(index)
    }

    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {
        remove(fromIndex, toIndex)
        super.removeMediaItems(fromIndex, toIndex)
    }

    override fun clearMediaItems() {
        tracked.clear()
        super.clearMediaItems()
    }

    private fun replaceAll(items: List<MediaItem>) {
        tracked.clear()
        tracked.addAll(items)
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
