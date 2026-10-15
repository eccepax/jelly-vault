package org.jellyfin.mobile.books.library

import app.cash.turbine.test
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.mobile.books.data.dao.BookProgressDao
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.data.dao.DownloadRecordDao
import org.jellyfin.mobile.testing.VaultTestServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Runs [BookLibraryViewModel] against the real Vault. Its sorting and tag filter work on the books
 * it loaded, so they only work if it asked the server for the fields they read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookLibraryContractTest {

    private lateinit var viewModel: BookLibraryViewModel

    @BeforeEach
    fun setUp() {
        VaultTestServer.assumeAvailable()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = BookLibraryViewModel(
            apiClient = VaultTestServer.apiClient,
            appPreferences = mockk<AppPreferences>(relaxed = true) {
                every { currentServerId } returns 1L
                every { currentUserId } returns 1L
            },
            bookProgressDao = mockk<BookProgressDao> { coEvery { getAllProgress() } returns emptyList() },
            bookDownloadManager = mockk(relaxed = true),
            downloadRecordDao = mockk<DownloadRecordDao> { every { getAll() } returns flowOf(emptyList()) },
            libraryId = null,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sorting by date added puts the most recently added books first`() = runBlocking<Unit> {
        val newestFirst = serverBooks("SortBy=DateCreated,SortName&SortOrder=Descending")
            .map { it["Name"]!!.jsonPrimitive.content }
        loaded()

        viewModel.sortOption.value = BookSortOption.DATE_ADDED

        viewModel.filteredBooks.test {
            expectMostRecentItem().map { it.name } shouldBe newestFirst
        }
    }

    @Test
    fun `books arrive with their tags, so the tag filter can offer them`() = runBlocking<Unit> {
        // The server leaves an item's tags out unless asked for them; then a book without any has [].
        loaded()

        viewModel.filteredBooks.value.forEach { book -> book.tags.shouldNotBeNull() }
    }

    private suspend fun loaded() = withTimeout(10.seconds) {
        viewModel.filteredBooks.first { it.isNotEmpty() }
        viewModel.loading.first { !it }
    }

    private fun serverBooks(query: String) =
        VaultTestServer.rawJson("/Items?IncludeItemTypes=Book&Recursive=true&$query")["Items"]!!
            .jsonArray.map { it.jsonObject }
}
