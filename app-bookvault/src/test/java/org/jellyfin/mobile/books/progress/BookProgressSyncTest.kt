package org.jellyfin.mobile.books.progress

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.books.data.BookDatabase
import org.jellyfin.mobile.books.data.dao.BookProgressDao
import org.jellyfin.mobile.books.data.entity.BookProgressEntity
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.model.extensions.ticks
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class BookProgressSyncTest {

    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        BookDatabase::class.java,
    )
        .allowMainThreadQueries()
        .build()
    private val dao = database.bookProgressDao
    private val server = FakeServer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var clock = 0L
    private val sync = BookProgressSync(dao, server, scope, now = { clock })

    private val book = UUID.fromString("3b832b64-bc56-ccf9-15d9-c608962c3acd")
    private val bookId = book.toString()
    private val morning = Instant.parse("2026-10-21T08:00:00Z")
    private val noon = Instant.parse("2026-10-21T12:00:00Z")

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    // --- where a book opens -----------------------------------------------------------------------

    @Test
    fun `a book read more recently on another device opens where it was left there`() = runTest {
        saved(page = 2, at = morning)
        server.progress[book] = onServer(positionTicks = 6_000_000L, at = noon)

        sync.startPage(bookId, totalPages = 10) shouldBe 6
    }

    @Test
    fun `the other device's place is kept on this one, and not sent back`() = runTest {
        saved(page = 2, at = morning)
        server.progress[book] = onServer(positionTicks = 6_000_000L, at = noon)

        sync.startPage(bookId, totalPages = 10)

        dao.getProgress(bookId) shouldBe BookProgressEntity(bookId, 6, 10, noon.toEpochMilli(), pendingSync = false)
    }

    @Test
    fun `a book read more recently on this device opens where it was left here`() = runTest {
        saved(page = 7, at = noon)
        server.progress[book] = onServer(positionTicks = 2_000_000L, at = morning)

        sync.startPage(bookId, totalPages = 10) shouldBe 7
    }

    @Test
    fun `a book first read on another device opens where it was left there`() = runTest {
        server.progress[book] = onServer(positionTicks = 3_000_000L, at = noon)

        sync.startPage(bookId, totalPages = 10) shouldBe 3
    }

    @Test
    fun `away from the server, a book opens where it was left here`() = runTest {
        saved(page = 7, at = morning)
        server.unreachable = true

        sync.startPage(bookId, totalPages = 10) shouldBe 7
    }

    @Test
    fun `a server too slow to answer doesn't hold the book up`() = runTest {
        saved(page = 7, at = morning)
        server.onAsk = { delay(BookProgressSync.READ_TIMEOUT_MS * 2) }

        sync.startPage(bookId, totalPages = 10) shouldBe 7
    }

    @Test
    fun `a server that refuses to answer leaves the book where it was left here`() = runTest {
        saved(page = 7, at = morning)
        server.refuses = true

        sync.startPage(bookId, totalPages = 10) shouldBe 7
    }

    @Test
    fun `a position past the end of the book, as BookVault used to send, is ignored`() = runTest {
        saved(page = 7, at = morning)
        server.progress[book] = onServer(positionTicks = 400_000_000L, at = noon)

        sync.startPage(bookId, totalPages = 10) shouldBe 7
    }

    @Test
    fun `a book never read anywhere opens at the start`() = runTest {
        server.progress[book] = ServerBookProgress(0, EPUB, played = false, lastPlayed = null)

        sync.startPage(bookId, totalPages = 10) shouldBe 0
    }

    @Test
    fun `a book with fewer chapters than its saved place opens at its last`() = runTest {
        saved(page = 40, at = noon, total = 50)

        sync.startPage(bookId, totalPages = 10) shouldBe 9
    }

    // --- telling the server -----------------------------------------------------------------------

    @Test
    fun `reading the server hasn't heard about is sent, as of when it happened`() = runTest {
        saved(page = 4, at = noon)
        server.progress[book] = onServer(positionTicks = 0, at = morning)

        sync.sync()

        server.recorded shouldBe listOf(Recorded(book, 4_000_000L.ticks, finished = false, at = noon))
    }

    @Test
    fun `reaching the last page is sent as finished, at that page`() = runTest {
        saved(page = 9, at = noon)
        server.progress[book] = onServer(positionTicks = 0, at = null)

        sync.sync()

        server.recorded.single() shouldBe Recorded(book, 9_000_000L.ticks, finished = true, at = noon)
    }

    @Test
    fun `opening a one-page book isn't sent as finishing it`() = runTest {
        saved(page = 0, at = noon, total = 1)
        server.progress[book] = onServer(positionTicks = 0, at = null)

        sync.sync()

        server.recorded.single().finished shouldBe false
    }

    // --- saving ------------------------------------------------------------------------------------

    @Test
    fun `a page saved here waits to be sent, as of when it was turned`() = runTest {
        clock = noon.toEpochMilli()

        sync.save(bookId, page = 3, totalPages = 10).join()

        dao.getPending() shouldBe listOf(BookProgressEntity(bookId, 3, 10, noon.toEpochMilli(), pendingSync = true))
    }

    @Test
    fun `forgetting the places leaves none for the next account, even one being saved`() = runTest {
        saved(page = 4, at = noon)
        val writing = CompletableDeferred<Unit>()
        val slowDao = object : BookProgressDao by dao {
            override suspend fun upsertProgress(entity: BookProgressEntity) {
                writing.await()
                dao.upsertProgress(entity)
            }
        }
        val sync = BookProgressSync(slowDao, server, scope, now = { clock })
        sync.save("96ac955d-ea41-d4a5-5448-c0beaedb5608", page = 5, totalPages = 10)

        val forgetting = scope.async { sync.forget() }
        writing.complete(Unit)
        forgetting.await()

        dao.getAllProgress().shouldBeEmpty()
    }

    @Test
    fun `leaving a book right after turning a page sends that page`() = runTest {
        clock = noon.toEpochMilli()
        server.progress[book] = onServer(positionTicks = 0, at = null)
        val writing = CompletableDeferred<Unit>()
        val slowDao = object : BookProgressDao by dao {
            override suspend fun upsertProgress(entity: BookProgressEntity) {
                writing.await()
                dao.upsertProgress(entity)
            }
        }
        val sync = BookProgressSync(slowDao, server, scope, now = { clock })

        sync.save(bookId, page = 5, totalPages = 10)
        val sending = sync.sendSoon()
        writing.complete(Unit)
        sending.join()

        server.recorded.single().position shouldBe 5_000_000L.ticks
    }

    @Test
    fun `once sent, it isn't sent again`() = runTest {
        saved(page = 4, at = noon)
        server.progress[book] = onServer(positionTicks = 0, at = null)

        sync.sync()
        sync.sync()

        server.recorded.size shouldBe 1
        dao.getPending().shouldBeEmpty()
    }

    @Test
    fun `reading on another device since then wins over it`() = runTest {
        saved(page = 4, at = morning)
        server.progress[book] = onServer(positionTicks = 8_000_000L, at = noon)

        sync.sync()

        server.recorded.shouldBeEmpty()
        dao.getPending().shouldBeEmpty()
    }

    @Test
    fun `a position BookVault used to send doesn't stop the real one going out`() = runTest {
        saved(page = 4, at = morning)
        server.progress[book] = onServer(positionTicks = 400_000_000L, at = noon)

        sync.sync()

        server.recorded.single().position shouldBe 4_000_000L.ticks
    }

    @Test
    fun `away from the server, it waits for next time`() = runTest {
        saved(page = 4, at = noon)
        server.unreachable = true

        sync.sync()

        dao.getPending().map { it.pageNumber } shouldBe listOf(4)
        server.unreachable = false
        server.progress[book] = onServer(positionTicks = 0, at = null)
        sync.sync()
        server.recorded.size shouldBe 1
    }

    @Test
    fun `a server that refuses it for good isn't asked again`() = runTest {
        saved(page = 4, at = noon)
        server.progress[book] = onServer(positionTicks = 0, at = null)
        server.recordFails = 400

        sync.sync()

        dao.getPending().shouldBeEmpty()
    }

    @Test
    fun `a server that isn't ready for it takes it next time`() = runTest {
        // Jellyfin restarting behind a proxy, overloaded, or the sign-in run out
        for (status in listOf(401, 403, 429, 500, 502, 503)) {
            saved(page = 4, at = noon)
            server.progress[book] = onServer(positionTicks = 0, at = null)
            server.recordFails = status

            sync.sync()

            dao.getPending().map { it.pageNumber } shouldBe listOf(4)
            server.recordFails = null
            sync.sync()
            dao.getPending().shouldBeEmpty()
        }
    }

    @Test
    fun `a server that never answers doesn't hold the places up`() = runTest(timeout = 30.seconds) {
        saved(page = 4, at = noon)
        server.onAsk = { awaitCancellation() }

        sync.sync()

        dao.getPending().map { it.pageNumber } shouldBe listOf(4)
    }

    @Test
    fun `a book the server doesn't know the length of yet waits until it does`() = runTest {
        saved(page = 4, at = noon)
        server.progress[book] = ServerBookProgress(0, runTimeTicks = null, played = false, lastPlayed = null)

        sync.sync()

        server.recorded.shouldBeEmpty()
        dao.getPending().map { it.pageNumber } shouldBe listOf(4)
        server.progress[book] = onServer(positionTicks = 0, at = null)
        sync.sync()
        server.recorded.single().position shouldBe 4_000_000L.ticks
    }

    @Test
    fun `a book gone from the server is let go`() = runTest {
        saved(page = 4, at = noon)

        sync.sync()

        server.recorded.shouldBeEmpty()
        dao.getPending().shouldBeEmpty()
    }

    @Test
    fun `every place waiting is looked up with one question to the server`() = runTest {
        val other = UUID.fromString("96ac955d-ea41-d4a5-5448-c0beaedb5608")
        saved(page = 4, at = noon)
        saved(page = 2, at = morning, id = other.toString())
        server.progress[book] = onServer(positionTicks = 0, at = null)
        server.progress[other] = onServer(positionTicks = 0, at = null)

        sync.sync()

        server.asks shouldBe 1
        server.recorded.size shouldBe 2
    }

    @Test
    fun `nothing waiting, nothing asked`() = runTest {
        sync.sync()

        server.asks shouldBe 0
    }

    @Test
    fun `reading on while it's being sent is sent next time`() = runTest {
        saved(page = 4, at = morning)
        server.progress[book] = onServer(positionTicks = 0, at = null)
        server.onAsk = {
            server.onAsk = {}
            saved(page = 5, at = noon)
        }

        sync.sync()

        dao.getPending().map { it.pageNumber } shouldBe listOf(5)
    }

    @Test
    fun `books are sent oldest first`() = runTest {
        val other = UUID.fromString("96ac955d-ea41-d4a5-5448-c0beaedb5608")
        saved(page = 4, at = noon)
        saved(page = 2, at = morning, id = other.toString())
        server.progress[book] = onServer(positionTicks = 0, at = null)
        server.progress[other] = onServer(positionTicks = 0, at = null)

        sync.sync()

        server.recorded.map { it.itemId } shouldBe listOf(other, book)
    }

    private suspend fun saved(page: Int, at: Instant, total: Int = 10, id: String = bookId) =
        dao.upsertProgress(BookProgressEntity(id, page, total, at.toEpochMilli(), pendingSync = true))

    private fun onServer(positionTicks: Long, at: Instant?) =
        ServerBookProgress(positionTicks, EPUB, played = false, lastPlayed = at)

    private data class Recorded(val itemId: UUID, val position: Duration, val finished: Boolean, val at: Instant)

    private class FakeServer : BookProgressServer {
        val progress = mutableMapOf<UUID, ServerBookProgress>()
        val recorded = mutableListOf<Recorded>()
        var unreachable = false
        var refuses = false
        var recordFails: Int? = null
        var onAsk: suspend () -> Unit = {}
        var asks = 0

        override suspend fun progress(bookIds: Collection<UUID>): Map<UUID, ServerBookProgress> {
            asks++
            if (unreachable) throw TimeoutException("Vault unreachable")
            if (refuses) throw InvalidStatusException(500)
            onAsk()
            return progress.filterKeys { it in bookIds }
        }

        override suspend fun lastPlayed(itemId: UUID): Instant? = progress[itemId]?.lastPlayed

        override suspend fun record(itemId: UUID, position: Duration, finished: Boolean, at: Instant) {
            recordFails?.let { throw InvalidStatusException(it) }
            recorded += Recorded(itemId, position, finished, at)
        }
    }

    private companion object {
        const val EPUB = 10_000_000L
    }
}
