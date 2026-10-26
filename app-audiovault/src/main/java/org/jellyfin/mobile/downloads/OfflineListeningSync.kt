package org.jellyfin.mobile.downloads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.jellyfin.mobile.common.server.ServerPositions
import org.jellyfin.mobile.common.server.worthRetrying
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import timber.log.Timber
import java.time.Instant
import java.util.UUID

/**
 * Sends the server the listening it hasn't heard about: positions in downloaded tracks saved on
 * the device (see [org.jellyfin.mobile.player.source.LocalJellyfinMediaSource.positionSavedAt]),
 * as when listening offline. Listening on another device since then wins over it.
 */
class OfflineListeningSync(
    private val dao: DownloadDao,
    private val server: ServerPositions,
    /** Whether a track is still downloaded; a removed one's details stay only until this is sent. */
    private val isKept: suspend (trackId: UUID) -> Boolean,
) {
    /**
     * Sends what's waiting, until the server can't be reached or isn't ready (restarting,
     * overloaded, signed out); the rest waits for next time.
     */
    suspend fun sync() {
        for (download in dao.getAllDownloads().first()) {
            val source = download.mediaSource
            val savedAt = source.positionSavedAt ?: continue
            try {
                val lastPlayed = server.lastPlayed(source.itemId)
                if (lastPlayed == null || lastPlayed.isBefore(savedAt)) {
                    server.record(source.itemId, source.startTime, source.finished, savedAt)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                if (e.worthRetrying()) return
                // Sending it again wouldn't change the server's mind
                Timber.w(e, "The server refused the listening in %s", source.itemId)
            }
            markSent(download.itemId, savedAt)
        }
    }

    /**
     * Clears what was sent, unless the player saved a newer position meanwhile; a removed track's
     * details go with it.
     */
    private suspend fun markSent(downloadId: String, savedAt: Instant) {
        val current = dao.get(downloadId)?.mediaSource ?: return
        if (current.positionSavedAt != savedAt) return
        if (!isKept(current.itemId)) {
            dao.delete(downloadId)
            return
        }
        current.positionSavedAt = null
        current.finished = false
        dao.insert(DownloadEntity(current))
    }
}
