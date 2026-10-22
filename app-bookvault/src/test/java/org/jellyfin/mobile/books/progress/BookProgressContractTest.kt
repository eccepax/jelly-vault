package org.jellyfin.mobile.books.progress

import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.testing.VaultFixtures.Books
import org.jellyfin.mobile.testing.VaultTestServer
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.extensions.ticks
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.time.Duration as JavaDuration

/**
 * A reader's place in a book, sent to the test server and read back the way BookVault reads it.
 * Writes the books' play data, so all of it is put back exactly after each test.
 */
class BookProgressContractTest {

    private val apiClient get() = VaultTestServer.apiClient
    private val server get() = JellyfinBookProgressServer(apiClient)
    private lateinit var epub: UUID
    private lateinit var pdf: UUID
    private val originals = mutableMapOf<UUID, UserItemDataDto>()

    @BeforeEach
    fun setUp() = runBlocking {
        VaultTestServer.assumeAvailable()
        epub = book(Books.SEARCH_TERM)
        pdf = book(PDF_SEARCH_TERM)
        for (id in listOf(epub, pdf)) originals[id] = apiClient.itemsApi.getItemUserData(id).content
    }

    /** The update call can't clear a field (null means "leave it"), so wipe the play data first. */
    @AfterEach
    fun restore() = runBlocking {
        for ((id, original) in originals) {
            apiClient.playStateApi.markUnplayedItem(id)
            apiClient.itemsApi.updateItemUserData(
                itemId = id,
                data = UpdateUserItemDataDto(
                    playbackPositionTicks = original.playbackPositionTicks,
                    playCount = original.playCount,
                    played = original.played,
                    lastPlayedDate = original.lastPlayedDate,
                ),
            )
        }
    }

    private suspend fun book(searchTerm: String): UUID = apiClient.itemsApi.getItems(
        includeItemTypes = listOf(BaseItemKind.BOOK),
        recursive = true,
        searchTerm = searchTerm,
    ).content.items.orEmpty().single().id!!

    @Test
    fun `the server counts a PDF's pages, so a page is a position in it`() = runBlocking<Unit> {
        server.progress(pdf).runTimeTicks shouldBe AARDVARK_PAGES * PDF_TICKS_PER_PAGE
    }

    @Test
    fun `a chapter sent to the server comes back as that chapter, as of when it was read`() = runBlocking<Unit> {
        val runTime = server.progress(epub).runTimeTicks.shouldNotBeNull()
        val readAt = Instant.now().minus(JavaDuration.ofHours(2))

        server.record(epub, pageTicks(page = 4, totalPages = 10, runTime).ticks, finished = false, at = readAt)

        val progress = server.progress(epub)
        progress.page(totalPages = 10) shouldBe 4
        val knownAt = progress.knownAt.shouldNotBeNull()
        JavaDuration.between(readAt, knownAt).abs().toMillis() shouldBeLessThan 60_000L
    }

    @Test
    fun `a PDF page sent to the server comes back as that page`() = runBlocking<Unit> {
        val runTime = server.progress(pdf).runTimeTicks.shouldNotBeNull()

        server.record(pdf, pageTicks(page = 1, totalPages = 3, runTime).ticks, finished = false, at = Instant.now())

        server.progress(pdf).page(totalPages = 3) shouldBe 1
    }

    @Test
    fun `a book finished comes back at its last page`() = runBlocking<Unit> {
        server.record(epub, 0L.ticks, finished = true, at = Instant.now())

        server.progress(epub).page(totalPages = 10) shouldBe 9
    }

    private companion object {
        const val PDF_SEARCH_TERM = "Aardvark"
        const val AARDVARK_PAGES = 3L
        const val PDF_TICKS_PER_PAGE = 10_000L
    }
}
