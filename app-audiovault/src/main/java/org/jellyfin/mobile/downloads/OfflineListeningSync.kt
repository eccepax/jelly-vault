package org.jellyfin.mobile.downloads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.jellyfin.mobile.common.server.isNetworkFailure
import org.jellyfin.mobile.data.dao.DownloadDao
import org.jellyfin.mobile.data.entity.DownloadEntity
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration

/** Where the server keeps the listener's place in each track. */
interface ServerPositions {
    /** When the listener last played [itemId] anywhere, per the server; null if never. */
    suspend fun lastPlayed(itemId: UUID): Instant?

    /** Records the listener at [position] in [itemId], or done with it if [finished], as of [at]. */
    suspend fun record(itemId: UUID, position: Duration, finished: Boolean, at: Instant)
}

/**
 * Sends the server the listening it hasn't heard about: positions in downloaded tracks saved on
 * the device (see [org.jellyfin.mobile.player.source.LocalJellyfinMediaSource.positionSavedAt]),
 * as when listening offline. Listening on another device since then wins over it.
 */
class OfflineListeningSync(
    private val dao: DownloadDao,
    private val server: ServerPositions,
) {
    /** Sends what's waiting, until the server can't be reached; the rest waits for next time. */
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
                if (e.isNetworkFailure()) return
                // Sending it again wouldn't change the server's mind
                Timber.w(e, "The server refused the listening in %s", source.itemId)
            }
            markSent(download.itemId, savedAt)
        }
    }

    /** Clears what was sent, unless the player saved a newer position meanwhile. */
    private suspend fun markSent(downloadId: String, savedAt: Instant) {
        val current = dao.get(downloadId)?.mediaSource ?: return
        if (current.positionSavedAt != savedAt) return
        current.positionSavedAt = null
        current.finished = false
        dao.insert(DownloadEntity(current))
    }
}
