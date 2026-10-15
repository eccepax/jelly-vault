package org.jellyfin.mobile.books.library

import io.kotest.matchers.shouldBe
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.mobile.books.data.dao.BookProgressDao
import org.jellyfin.mobile.books.downloads.BookDownloadManager
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.data.dao.DownloadRecordDao
import org.jellyfin.mobile.common.data.entity.DownloadRecord
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.Response
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class BookLibraryViewModelTest {

    private val apiClient = mockk<ApiClient>()
    private val itemsApi = mockk<ItemsApi>()
    private val appPreferences = mockk<AppPreferences>(relaxed = true)
    private val bookDownloadManager = mockk<BookDownloadManager>(relaxed = true)
    private val downloads = MutableStateFlow<List<DownloadRecord>>(emptyList())

    private val dune = book("Dune", author = "Frank Herbert", series = "Dune Chronicles")
    private val emma = book("Emma", author = "Jane Austen")
    private val ubik = book("Ubik", author = "Philip K. Dick")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // The api client's *Api properties are extensions with real logic; stub them directly.
        mockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
        every { apiClient.itemsApi } returns itemsApi
        every { appPreferences.currentServerId } returns 1L
        every { appPreferences.currentUserId } returns 1L
        serverHas(dune, emma, ubik)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
        Dispatchers.resetMain()
    }

    @Test
    fun `loads the server's books`() = runBlocking<Unit> {
        val viewModel = loadedViewModel()

        viewModel.filteredBooks.shouldBecome(listOf(dune, emma, ubik))
        viewModel.error.value shouldBe null
    }

    @Test
    fun `says so instead of loading when not signed in to a server`() = runBlocking<Unit> {
        every { appPreferences.currentUserId } returns null

        val viewModel = loadedViewModel()

        viewModel.error.value shouldBe "Not connected to a server"
        coVerify(exactly = 0) { getItems() }
    }

    @Test
    fun `a server error is shown and loading ends`() = runBlocking<Unit> {
        coEvery { getItems() } throws InvalidStatusException(500)

        val viewModel = loadedViewModel()

        (viewModel.error.value != null) shouldBe true
        viewModel.loading.value shouldBe false
    }

    @Test
    fun `loads only the chosen library's books`() = runBlocking<Unit> {
        val libraryId = UUID.randomUUID()

        loadedViewModel(libraryId = libraryId.toString())

        coVerify { getItems(parentId = libraryId) }
    }

    @Test
    fun `searches titles, authors and series, ignoring case`() = runBlocking<Unit> {
        val viewModel = loadedViewModel()

        viewModel.searchQuery.value = "  AUSTEN "
        viewModel.filteredBooks.shouldBecome(listOf(emma))

        viewModel.searchQuery.value = "chronicles"
        viewModel.filteredBooks.shouldBecome(listOf(dune))

        viewModel.searchQuery.value = "ubi"
        viewModel.filteredBooks.shouldBecome(listOf(ubik))
    }

    @Test
    fun `downloaded only shows just the books on the device, and follows new downloads`() = runBlocking<Unit> {
        downloads.value = listOf(downloaded(emma))
        val viewModel = loadedViewModel()

        viewModel.showDownloadedOnly.value = true
        viewModel.filteredBooks.shouldBecome(listOf(emma))

        downloads.value = listOf(downloaded(emma), downloaded(ubik))
        viewModel.filteredBooks.shouldBecome(listOf(emma, ubik))
    }

    @Test
    fun `a failed download doesn't crash the library`() = runBlocking<Unit> {
        coEvery { bookDownloadManager.downloadBook(any()) } throws IOException("disk full")
        val viewModel = loadedViewModel()

        viewModel.downloadBook(dune)

        coVerify { bookDownloadManager.downloadBook(dune) }
    }

    @Test
    fun `deleting a download removes that book's file`() = runBlocking<Unit> {
        val viewModel = loadedViewModel()
        val duneId = dune.id.toString()

        viewModel.deleteDownload(dune)

        coVerify { bookDownloadManager.deleteDownload(duneId) }
    }

    private suspend fun loadedViewModel(libraryId: String? = null) = BookLibraryViewModel(
        apiClient = apiClient,
        appPreferences = appPreferences,
        bookProgressDao = mockk<BookProgressDao> { coEvery { getAllProgress() } returns emptyList() },
        bookDownloadManager = bookDownloadManager,
        downloadRecordDao = mockk<DownloadRecordDao> { every { getAll() } returns downloads },
        libraryId = libraryId,
    ).also { viewModel -> withTimeout(5.seconds) { viewModel.loading.first { !it } } }

    /** The filters run through a combine, so they land a moment after the input that changed them. */
    private suspend fun <T> StateFlow<T>.shouldBecome(expected: T) {
        withTimeoutOrNull(5.seconds) { first { it == expected } }
        value shouldBe expected
    }

    private fun serverHas(vararg books: BaseItemDto) {
        coEvery { getItems() } returns Response(
            BaseItemDtoQueryResult(items = books.toList(), totalRecordCount = books.size, startIndex = 0),
            200,
            emptyMap(),
        )
    }

    /** The parameters [BookLibraryViewModel] sets; the rest fall back to the same defaults on both sides. */
    private suspend fun MockKMatcherScope.getItems(parentId: UUID? = any()) = itemsApi.getItems(
        parentId = parentId,
        fields = any(),
        includeItemTypes = any(),
        recursive = any(),
        sortOrder = any(),
        sortBy = any(),
    )

    private fun book(name: String, author: String, series: String? = null) = mockk<BaseItemDto>(relaxed = true) {
        every { id } returns UUID.randomUUID()
        every { this@mockk.name } returns name
        every { albumArtist } returns author
        every { seriesName } returns series
        every { tags } returns emptyList()
        every { dateCreated } returns null
    }

    private fun downloaded(book: BaseItemDto) = mockk<DownloadRecord>(relaxed = true) {
        every { itemId } returns book.id.toString()
    }
}
