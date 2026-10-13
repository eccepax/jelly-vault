package org.jellyfin.mobile.music.domain

import kotlin.random.Random

/** One slot in the queue. [uid] tells apart two queued copies of the same [track]. */
data class QueueEntry(val uid: Long, val track: Track)

/**
 * Immutable play queue: [entries] in play order, with [currentIndex] pointing at the playing one.
 * Every operation returns a new queue; nothing here knows about the player.
 */
@ConsistentCopyVisibility
data class PlayQueue private constructor(
    val entries: List<QueueEntry>,
    val currentIndex: Int,
    val repeatMode: RepeatMode,
    private val nextUid: Long,
    /** The pre-shuffle play order, or null when not shuffled. Kept in sync by every edit. */
    private val originalEntries: List<QueueEntry>?,
) {
    val isShuffled: Boolean get() = originalEntries != null

    val tracks: List<Track> get() = entries.map { it.track }

    val current: Track? get() = entries.getOrNull(currentIndex)?.track

    val hasNext: Boolean
        get() = entries.isNotEmpty() && (currentIndex < entries.lastIndex || repeatMode == RepeatMode.ALL)

    val hasPrevious: Boolean get() = entries.isNotEmpty() && (currentIndex > 0 || repeatMode == RepeatMode.ALL)

    fun withRepeatMode(mode: RepeatMode): PlayQueue = copy(repeatMode = mode)

    /** User-initiated skip forward. Moves on even with [RepeatMode.ONE]; stays put at the end with [RepeatMode.OFF]. */
    fun skipToNext(): PlayQueue = when {
        !hasNext -> this
        currentIndex == entries.lastIndex -> copy(currentIndex = 0)
        else -> copy(currentIndex = currentIndex + 1)
    }

    /** User-initiated skip back. Wraps to the end only with [RepeatMode.ALL]. */
    fun skipToPrevious(): PlayQueue = when {
        !hasPrevious -> this
        currentIndex == 0 -> copy(currentIndex = entries.lastIndex)
        else -> copy(currentIndex = currentIndex - 1)
    }

    /** The current track played to completion. Returns null when playback should stop. */
    fun onTrackEnded(): PlayQueue? = when {
        entries.isEmpty() -> null
        repeatMode == RepeatMode.ONE -> this
        hasNext -> skipToNext()
        else -> null
    }

    fun skipTo(index: Int): PlayQueue = if (index in entries.indices) copy(currentIndex = index) else this

    /** Inserts [newTracks] right after the current track. */
    fun playNext(newTracks: List<Track>): PlayQueue {
        if (entries.isEmpty()) return addToQueue(newTracks)
        val (newEntries, queue) = wrap(newTracks)
        val currentUid = entries[currentIndex].uid
        return queue.copy(
            entries = entries.insertedAfter(currentIndex, newEntries),
            originalEntries = originalEntries?.let { original ->
                original.insertedAfter(original.indexOfFirst { it.uid == currentUid }, newEntries)
            },
        )
    }

    fun addToQueue(newTracks: List<Track>): PlayQueue {
        val (newEntries, queue) = wrap(newTracks)
        return queue.copy(entries = entries + newEntries, originalEntries = originalEntries?.plus(newEntries))
    }

    /** Removes the track at [index]. Removing the current track makes the following one current. */
    fun remove(index: Int): PlayQueue {
        if (index !in entries.indices) return this
        val removedUid = entries[index].uid
        val remaining = entries.filterIndexed { i, _ -> i != index }
        val newCurrent = when {
            index < currentIndex -> currentIndex - 1
            else -> currentIndex.coerceAtMost((remaining.size - 1).coerceAtLeast(0))
        }
        return copy(
            entries = remaining,
            currentIndex = newCurrent,
            originalEntries = originalEntries?.filterNot { it.uid == removedUid },
        )
    }

    /** Moves the track at [from] to [to], keeping the same track current. */
    fun move(from: Int, to: Int): PlayQueue {
        if (from !in entries.indices || to !in entries.indices) return this
        val reordered = entries.toMutableList().apply { add(to, removeAt(from)) }
        val newCurrent = when {
            currentIndex == from -> to
            currentIndex in (from + 1)..to -> currentIndex - 1
            currentIndex in to until from -> currentIndex + 1
            else -> currentIndex
        }
        return copy(entries = reordered, currentIndex = newCurrent)
    }

    /**
     * Shuffles everything except the current track, which moves to the front so playback continues
     * uninterrupted. Reshuffling keeps the original order from before the first shuffle.
     */
    fun shuffled(random: Random): PlayQueue {
        if (entries.isEmpty()) return this
        val current = entries[currentIndex]
        val rest = entries.filterIndexed { i, _ -> i != currentIndex }.shuffled(random)
        return copy(entries = listOf(current) + rest, currentIndex = 0, originalEntries = originalEntries ?: entries)
    }

    /** Restores the pre-shuffle order (including any edits made since), keeping the current track. */
    fun unshuffled(): PlayQueue {
        val original = originalEntries ?: return this
        val currentUid = entries.getOrNull(currentIndex)?.uid
        return copy(
            entries = original,
            currentIndex = original.indexOfFirst { it.uid == currentUid }.coerceAtLeast(0),
            originalEntries = null,
        )
    }

    /** Assigns fresh uids to [newTracks], returning them with this queue's uid counter advanced past them. */
    private fun wrap(newTracks: List<Track>): Pair<List<QueueEntry>, PlayQueue> {
        val newEntries = newTracks.mapIndexed { i, track -> QueueEntry(nextUid + i, track) }
        return newEntries to copy(nextUid = nextUid + newTracks.size)
    }

    companion object {
        val EMPTY = PlayQueue(
            entries = emptyList(),
            currentIndex = 0,
            repeatMode = RepeatMode.OFF,
            nextUid = 0,
            originalEntries = null,
        )

        fun of(tracks: List<Track>, startIndex: Int = 0): PlayQueue =
            EMPTY.addToQueue(tracks).skipTo(startIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0)))
    }
}

private fun List<QueueEntry>.insertedAfter(index: Int, inserted: List<QueueEntry>): List<QueueEntry> =
    subList(0, index + 1) + inserted + subList(index + 1, size)
