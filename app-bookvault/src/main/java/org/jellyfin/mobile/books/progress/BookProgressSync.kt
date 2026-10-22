package org.jellyfin.mobile.books.progress

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.mobile.books.data.dao.BookProgressDao
import org.jellyfin.mobile.books.data.entity.BookProgressEntity
import org.jellyfin.mobile.common.server.ServerPositions
import org.jellyfin.mobile.common.server.isNetworkFailure
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber
import java.time.Instant
import java.util.UUID

/** Where the server keeps the reader's place in each book. */
interface BookProgressServer : ServerPositions {
    suspend fun progress(bookId: UUID): ServerBookProgress
}

/**
 * Keeps the reader's place in each book the same on every device: the place read most recently,
 * here or elsewhere, wins. Places saved here are sent when the server can be reached, and wait on
 * the device ([BookProgressEntity.pendingSync]) until then.
 */
class BookProgressSync(
    private val dao: BookProgressDao,
    private val server: BookProgressServer,
    /** Where [sendSoon] sends from: one that outlives the reader, so leaving it doesn't cut the send off. */
    private val scope: CoroutineScope? = null,
) {
    private val sending = Mutex()

    /**
     * The page [bookId] opens at: its place here, or the server's if read more recently elsewhere.
     * A server that can't answer quickly is left out; the book opens as it was left here.
     */
    suspend fun startPage(bookId: String, totalPages: Int): Int {
        if (totalPages <= 0) return 0
        val local = dao.getProgress(bookId)
        val remote = newerOnServer(bookId, local, totalPages)
            ?: return local?.pageNumber?.coerceIn(0, totalPages - 1) ?: 0
        // Kept here as the server has it: nothing new to send back
        dao.upsertProgress(remote)
        return remote.pageNumber
    }

    /** The server's place in [bookId], if it has a usable one read more recently than [local]. */
    private suspend fun newerOnServer(
        bookId: String,
        local: BookProgressEntity?,
        totalPages: Int,
    ): BookProgressEntity? {
        val remote = ask { withTimeoutOrNull(READ_TIMEOUT_MS) { server.progress(bookId.toUUID()) } }
        val remoteAt = remote?.knownAt ?: return null
        val newer = local == null || remoteAt.isAfter(Instant.ofEpochMilli(local.lastUpdated))
        val page = remote.page(totalPages)?.takeIf { newer } ?: return null
        return BookProgressEntity(bookId, page, totalPages, remoteAt.toEpochMilli(), pendingSync = false)
    }

    /** Sends the places the server hasn't heard about, until it can't be reached; the rest wait for next time. */
    suspend fun sync() = sending.withLock {
        for (saved in dao.getPending()) {
            try {
                send(saved)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                if (e.isNetworkFailure()) return@withLock
                // Sending it again wouldn't change the server's mind
                Timber.w(e, "The server refused the place in %s", saved.bookItemId)
            }
            dao.markSent(saved.bookItemId, saved.lastUpdated)
        }
    }

    /** [sync] in the background, for when the reader leaves a book. */
    fun sendSoon() {
        scope?.launch { sync() }
    }

    private suspend fun send(saved: BookProgressEntity) {
        val bookId = saved.bookItemId.toUUID()
        val remote = server.progress(bookId)
        val savedAt = Instant.ofEpochMilli(saved.lastUpdated)
        val runTime = remote.runTimeTicks?.takeIf { it > 0 } ?: return
        // Reading on another device since then wins over it
        val remoteAt = remote.knownAt
        if (remoteAt != null && !remoteAt.isBefore(savedAt)) return
        val finished = saved.pageNumber >= saved.totalPages - 1
        server.record(bookId, pageTicks(saved.pageNumber, saved.totalPages, runTime).ticks, finished, savedAt)
    }

    /** What [request] answers, or null if the server can't be reached or won't say. */
    private suspend fun ask(request: suspend () -> ServerBookProgress?): ServerBookProgress? = try {
        request()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Timber.w(e, "Couldn't ask the server for a book's place")
        null
    }

    internal companion object {
        const val READ_TIMEOUT_MS = 3_000L
    }
}
