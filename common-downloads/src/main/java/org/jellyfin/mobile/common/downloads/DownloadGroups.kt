package org.jellyfin.mobile.common.downloads

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.common.downloads.ItemDownload.State

/** How far along a downloaded group is, as a whole. */
sealed interface GroupStatus {
    data object NotDownloaded : GroupStatus
    data class InProgress(val done: Int, val total: Int) : GroupStatus

    /** Some items are held back, e.g. waiting for Wi-Fi. */
    data class Waiting(val done: Int, val total: Int) : GroupStatus
    data class Failed(val failed: Int, val total: Int) : GroupStatus
    data object Downloaded : GroupStatus
}

/** A downloaded group and its items, in order. */
data class DownloadGroup(val id: String, val itemIds: List<String>)

/**
 * Things kept on the device as a whole (an album, a playlist, an audiobook's chapters, a book), each
 * a group of items. Each item is fetched once however many groups include it, and deleted only when
 * no group needs it any more. What the groups and items are is up to the app.
 */
class DownloadGroups(
    private val downloads: ItemDownloads,
    private val dao: DownloadGroupDao,
    /** Where to fetch an item from. */
    private val uriOf: (itemId: String) -> String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Downloaded groups, newest first. */
    val groups: Flow<List<DownloadGroup>> = combine(dao.groups(), dao.items()) { groups, items ->
        val itemsByGroup = items.groupBy { it.groupId }
        groups.map { group ->
            DownloadGroup(group.id, itemsByGroup[group.id].orEmpty().sortedBy { it.position }.map { it.itemId })
        }
    }

    /** Every downloaded group's status, by id. */
    val statuses: Flow<Map<String, GroupStatus>> =
        combine(dao.groups(), dao.items(), downloads.downloads) { groups, items, downloads ->
            val itemsByGroup = items.groupBy { it.groupId }
            groups.associate { group ->
                group.id to statusOf(itemsByGroup[group.id].orEmpty().map { downloads[it.itemId]?.state })
            }
        }.distinctUntilChanged()

    /** Items fully on the device. */
    val downloadedItemIds: Flow<Set<String>> = combine(dao.items(), downloads.downloads) { items, downloads ->
        items.mapTo(mutableSetOf()) { it.itemId }.filterTo(mutableSetOf()) { downloads[it]?.state == State.DONE }
    }.distinctUntilChanged()

    val bytesUsed: Flow<Long> = downloads.downloads.map { downloads -> downloads.values.sumOf { it.bytesDownloaded } }
        .distinctUntilChanged()

    /** Whether anything is kept or on its way, even before a byte of it arrives (e.g. waiting for Wi-Fi). */
    val hasDownloads: Flow<Boolean> = combine(dao.groups(), downloads.downloads) { groups, downloads ->
        groups.isNotEmpty() || downloads.isNotEmpty()
    }.distinctUntilChanged()

    fun status(groupId: String): Flow<GroupStatus> =
        statuses.map { it[groupId] ?: GroupStatus.NotDownloaded }.distinctUntilChanged()

    suspend fun contains(groupId: String): Boolean = dao.exists(groupId)

    suspend fun itemIdsOf(groupId: String): List<String> = dao.itemIdsOf(groupId)

    /** Keeps group [groupId] with exactly [itemIds], in order, fetching any the device doesn't have. */
    suspend fun save(groupId: String, itemIds: List<String>) {
        val before = dao.itemIdsOf(groupId)
        dao.save(groupId, now(), itemIds)
        val held = downloads.downloads.value
        itemIds.distinct().filter { it !in held }.forEach(::fetch)
        deleteUnneeded(before - itemIds.toSet())
    }

    /** Brings a downloaded group in line with [itemIds]; does nothing if it isn't downloaded. */
    suspend fun update(groupId: String, itemIds: List<String>) {
        if (!dao.exists(groupId) || dao.itemIdsOf(groupId) == itemIds) return
        save(groupId, itemIds)
    }

    suspend fun remove(groupId: String) {
        val before = dao.itemIdsOf(groupId)
        dao.deleteGroup(groupId)
        deleteUnneeded(before)
    }

    /**
     * Deletes every group and every item, including any no group knows about. Not cut off part way
     * when the caller goes away, which would leave files on the device that no group knows about.
     */
    suspend fun removeAll() = withContext(NonCancellable) {
        dao.deleteAllGroups()
        downloads.downloads.value.keys.forEach(downloads::remove)
    }

    /** Fetches again the items of [groupId] that failed. */
    suspend fun retry(groupId: String) {
        val held = downloads.downloads.value
        dao.itemIdsOf(groupId).distinct().filter { held[it]?.state == State.FAILED }.forEach(::fetch)
    }

    /** At launch: fetches again any item a group needs but the downloader no longer has. */
    suspend fun repair() {
        downloads.isReady.first { it }
        val held = downloads.downloads.value
        dao.allItemIds().filter { it !in held }.forEach(::fetch)
    }

    /** Deletes those of [candidates] that no group needs any more. */
    private suspend fun deleteUnneeded(candidates: Collection<String>) {
        if (candidates.isEmpty()) return
        val needed = dao.allItemIds().toSet()
        candidates.distinct().filter { it !in needed }.forEach(downloads::remove)
    }

    private fun statusOf(states: List<State?>): GroupStatus {
        val done = states.count { it == State.DONE }
        val failed = states.count { it == State.FAILED }
        return when {
            failed > 0 -> GroupStatus.Failed(failed, states.size)
            done == states.size -> GroupStatus.Downloaded
            State.WAITING in states -> GroupStatus.Waiting(done, states.size)
            else -> GroupStatus.InProgress(done, states.size)
        }
    }

    private fun fetch(itemId: String) = downloads.download(itemId, uriOf(itemId))
}
