package org.jellyfin.mobile.books.progress

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.mobile.books.data.dao.BookProgressDao
import org.jellyfin.mobile.books.data.entity.BookProgressEntity
import org.jellyfin.mobile.common.server.ServerPositions
import org.jellyfin.mobile.common.server.worthRetrying
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber
import java.time.Instant
import java.util.UUID

/** Where the server keeps the reader's place in each book. */
interface BookProgressServer : ServerPositions {
    /** The reader's place in each of [bookIds] the server has; a book it doesn't have is left out. */
    suspend fun progress(bookIds: Collection<UUID>): Map<UUID, ServerBookProgress>
}

/**
 * Keeps the reader's place in each book the same on every device: the place read most recently,
 * here or elsewhere, wins. Places saved here are sent when the server can be reached, and wait on
 * the device ([BookProgressEntity.pendingSync]) until then.
 */
class BookProgressSync(
    private val dao: BookProgressDao,
    private val server: BookProgressServer,
    /** Where [save] and [sendSoon] run: one that outlives the reader, so leaving it doesn't cut them off. */
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val sending = Mutex()

    /** The last [save], which a send waits for so it doesn't miss the page just turned. */
    @Volatile
    private var saving: Job? = null

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
        val id = bookId.toUUID()
        val remote = ask(READ_TIMEOUT_MS) { server.progress(listOf(id))[id] }?.takeIf { it.isNewerThan(local) }
        val page = remote?.page(totalPages) ?: return null
        return BookProgressEntity(bookId, page, totalPages, remote.knownAt!!.toEpochMilli(), pendingSync = false)
    }

    /** Saves the reader at [page] of [totalPages] in [bookId], to be sent; in order, after any save before it. */
    fun save(bookId: String, page: Int, totalPages: Int): Job {
        val place = BookProgressEntity(bookId, page, totalPages, lastUpdated = now(), pendingSync = true)
        val before = saving
        return scope.launch {
            before?.join()
            dao.upsertProgress(place)
        }.also { saving = it }
    }

    /**
     * Sends the places the server hasn't heard about, all looked up with one question. They wait for
     * next time while the server can't be reached or isn't ready (restarting, overloaded, signed out).
     */
    suspend fun sync() = sending.withLock {
        val pending = dao.getPending().ifEmpty { return@withLock }
        val onServer = ask(SEND_TIMEOUT_MS) { server.progress(pending.map { it.bookItemId.toUUID() }) }
            ?: return@withLock
        for (saved in pending) {
            val remote = onServer[saved.bookItemId.toUUID()]
            val runTime = remote?.runTimeTicks?.takeIf { it > 0 }
            when {
                remote == null -> Timber.w("%s is gone from the server; its place is let go", saved.bookItemId)
                // Until the server knows how long the book is, a page can't be put as a place in it
                runTime == null -> continue
                // Reading on another device since then wins over it
                remote.isNewerThan(saved) -> Unit
                !send(saved, runTime) -> return@withLock
            }
            dao.markSent(saved.bookItemId, saved.lastUpdated)
        }
    }

    /** [sync] in the background, for when the reader leaves a book: after the page just turned is saved. */
    fun sendSoon(): Job {
        val saved = saving
        return scope.launch {
            saved?.join()
            sync()
        }
    }

    /**
     * Forgets every place here, on signing out: they're the account's, and the next one to sign in
     * mustn't open its books at them or send them as its own. Waits for a page still being saved.
     */
    suspend fun forget() {
        saving?.join()
        dao.deleteAll()
    }

    /** Sends [saved]: true once the server has it or has refused it for good, false to try again later. */
    private suspend fun send(saved: BookProgressEntity, runTimeTicks: Long): Boolean = try {
        withTimeoutOrNull(SEND_TIMEOUT_MS) {
            server.record(
                itemId = saved.bookItemId.toUUID(),
                position = pageTicks(saved.pageNumber, saved.totalPages, runTimeTicks).ticks,
                finished = readingState(saved.pageNumber, saved.totalPages) == ReadingState.FINISHED,
                at = Instant.ofEpochMilli(saved.lastUpdated),
            )
        } != null
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        if (e.worthRetrying()) {
            Timber.w(e, "Couldn't send the place in %s; it waits for next time", saved.bookItemId)
            false
        } else {
            // Sending it again wouldn't change the server's mind
            Timber.w(e, "The server refused the place in %s", saved.bookItemId)
            true
        }
    }

    /** What [request] answers within [timeoutMs], or null if the server can't be reached, is too slow, or won't say. */
    private suspend fun <T : Any> ask(timeoutMs: Long, request: suspend () -> T?): T? = try {
        withTimeoutOrNull(timeoutMs) { request() }
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Timber.w(e, "Couldn't ask the server for the books' places")
        null
    }

    internal companion object {
        /** How long a book waits to open for the server's place in it. */
        const val READ_TIMEOUT_MS = 3_000L

        /** How long sending waits on each request before leaving the rest for next time. */
        const val SEND_TIMEOUT_MS = 10_000L
    }
}
