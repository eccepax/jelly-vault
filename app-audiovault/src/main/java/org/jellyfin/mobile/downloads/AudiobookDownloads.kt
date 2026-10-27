package org.jellyfin.mobile.downloads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.jellyfin.mobile.common.downloads.DownloadGroups
import org.jellyfin.mobile.common.downloads.GroupStatus
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import timber.log.Timber
import java.util.UUID

/**
 * Audiobook tracks kept on the device. Each track is its own download group of one file (see
 * [DownloadGroups]), and its details from the server are kept alongside, so it plays with no
 * connection. Track ids are in their dashed form.
 */
class AudiobookDownloads(
    private val groups: DownloadGroups,
    private val dao: DownloadDao,
    /** What the server says about a track, for playing it offline; null if it can't be loaded. */
    private val detailsOf: suspend (trackId: String) -> DownloadEntity?,
) {
    /** Tracks whose file is fully on the device. */
    val downloadedIds: Flow<Set<String>> = groups.downloadedItemIds

    /** Tracks held back from downloading, waiting for Wi-Fi (Settings' Wi-Fi only). */
    val waitingIds: Flow<Set<String>> = groups.statuses.map { statuses ->
        statuses.filterValues { it is GroupStatus.Waiting }.keys
    }

    /** How much space the tracks' files take. */
    val bytesUsed: Flow<Long> = groups.bytesUsed

    /** Whether any track is downloaded or on its way, even one waiting for Wi-Fi. */
    val hasDownloads: Flow<Boolean> = groups.hasDownloads

    /** Downloaded tracks' details, for the downloads screen: only those whose file is complete. */
    val finished: Flow<List<DownloadEntity>> = combine(dao.getAllDownloads(), downloadedIds) { all, done ->
        all.filter { it.mediaSource.itemId.toString() in done }
    }

    /** [trackId] played from the device, from where it was left, if its file is fully downloaded. */
    suspend fun playable(trackId: UUID): LocalJellyfinMediaSource? {
        if (trackId.toString() !in downloadedIds.first()) return null
        return dao.getAllDownloads().first().firstOrNull { it.mediaSource.itemId == trackId }?.asMediaSource()
    }

    /** Downloads [trackIds], or tries again those whose download failed. One track failing never stops the rest. */
    suspend fun download(trackIds: List<String>) {
        for (trackId in trackIds) {
            val details = try {
                detailsOf(trackId)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.w(e, "Couldn't load the details of %s", trackId)
                null
            }
            if (details == null) {
                Timber.w("Not downloading %s: its details couldn't be loaded", trackId)
                continue
            }
            dao.insert(details.keepingUnsentListening(dao.get(details.itemId)))
            groups.save(trackId, listOf(trackId))
            groups.retry(trackId)
        }
    }

    /**
     * Deletes [trackIds]' files and their details, except listening the server hasn't heard about
     * yet: that stays until [OfflineListeningSync] sends it.
     */
    suspend fun remove(trackIds: List<String>) {
        trackIds.forEach { groups.remove(it) }
        val removed = trackIds.toSet()
        dao.delete(
            dao.getAllDownloads().first()
                .filter { it.mediaSource.itemId.toString() in removed && it.mediaSource.positionSavedAt == null }
                .map { it.itemId },
        )
    }

    /**
     * Deletes every downloaded track and its details. Listening the server hasn't heard about yet
     * stays to be sent, unless [forgetUnsentListening] went first.
     */
    suspend fun removeAll() {
        groups.removeAll()
        dao.delete(dao.getAllDownloads().first().filter { it.mediaSource.positionSavedAt == null }.map { it.itemId })
    }

    /**
     * Signing out: listening that couldn't be sent is the account's, not the next one's. A removed
     * track's details, kept only to send it, go with it.
     */
    suspend fun forgetUnsentListening() {
        val unsent = dao.getAllDownloads().first().filter { it.mediaSource.positionSavedAt != null }
        val (kept, removed) = unsent.partition { isKept(it.mediaSource.itemId) }
        dao.delete(removed.map { it.itemId })
        kept.forEach { download ->
            download.mediaSource.positionSavedAt = null
            download.mediaSource.finished = false
            dao.insert(download)
        }
    }

    /** Whether [trackId] is downloaded or on its way, rather than removed. */
    suspend fun isKept(trackId: UUID): Boolean = groups.contains(trackId.toString())

    /** At launch: fetches again any downloaded track whose file is gone. */
    suspend fun repair() = groups.repair()
}

/**
 * Fresh details from the server, with the place in [kept] if the server hasn't heard about it
 * yet: downloading a removed track again before that's sent mustn't lose it.
 */
private fun DownloadEntity.keepingUnsentListening(kept: DownloadEntity?): DownloadEntity {
    val unsent = kept?.mediaSource?.takeIf { it.positionSavedAt != null } ?: return this
    mediaSource.startTime = unsent.startTime
    mediaSource.finished = unsent.finished
    mediaSource.positionSavedAt = unsent.positionSavedAt
    return this
}
