package org.jellyfin.mobile.downloads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.jellyfin.mobile.common.downloads.DownloadGroups
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
            dao.insert(details)
            groups.save(trackId, listOf(trackId))
            groups.retry(trackId)
        }
    }

    suspend fun remove(trackId: String) {
        groups.remove(trackId)
        dao.getAllDownloads().first()
            .filter { it.mediaSource.itemId.toString() == trackId }
            .forEach { dao.delete(it.itemId) }
    }

    /** Deletes every downloaded track and its details. */
    suspend fun removeAll() {
        groups.removeAll()
        dao.deleteAll()
    }

    /** At launch: fetches again any downloaded track whose file is gone. */
    suspend fun repair() = groups.repair()
}
