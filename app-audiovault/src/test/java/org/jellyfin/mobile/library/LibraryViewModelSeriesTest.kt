package org.jellyfin.mobile.library

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.jupiter.api.Test
import java.util.UUID

/** [LibraryViewModel] showing a book of several files as separate books (a series), and as one again. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelSeriesTest : LibraryViewModelTestBase() {

    private val potterKey = "B:/books/Rowling:harry potter"

    private fun potter(n: Int) = BaseItemDto(
        id = UUID.randomUUID(),
        name = "HP$n",
        type = BaseItemKind.AUDIO_BOOK,
        path = "/books/Rowling/HP$n.m4b",
        album = "Harry Potter",
        indexNumber = n,
    )

    private fun wholeLibraryFromServer(vararg items: BaseItemDto) {
        every { appPreferences.libraryGlobalFlattened } returns true
        coEvery {
            userViewsApi.getUserViews(userId = any(), includeHidden = any())
        } returns queryResult(listOf(libraryView("Books", CollectionType.BOOKS)))
        stubItems(items.toList())
    }

    @Test
    fun `a book of several files is one book, until asked to be separate books`() = runTest {
        wholeLibraryFromServer(potter(1), potter(2))

        val viewModel = createViewModel()

        viewModel.itemNamesShouldBecome(listOf("Harry Potter"))
    }

    @Test
    fun `a book asked to be separate books shows as one book per file`() = runTest {
        wholeLibraryFromServer(potter(2), potter(1))
        every { audiobookEntryDao.separateBooks() } returns flowOf(listOf(potterKey))

        val viewModel = createViewModel()

        viewModel.itemNamesShouldBecome(listOf("HP1", "HP2"))
    }

    @Test
    fun `asking for separate books remembers it and shows the library again`() = runTest {
        wholeLibraryFromServer(potter(1), potter(2))
        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.showAsSeparateBooks(potterKey)

        coVerify(timeout = SETTLE_MS) { audiobookEntryDao.showAsSeparateBooks(potterKey) }
        coVerify(atLeast = 2, timeout = SETTLE_MS) { userViewsApi.getUserViews(userId = any(), includeHidden = any()) }
    }

    @Test
    fun `asking for one book again forgets it and shows the library again`() = runTest {
        wholeLibraryFromServer(potter(1), potter(2))
        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.showAsOneBook(potterKey)

        coVerify(timeout = SETTLE_MS) { audiobookEntryDao.showAsOneBook(potterKey) }
        coVerify(atLeast = 2, timeout = SETTLE_MS) { userViewsApi.getUserViews(userId = any(), includeHidden = any()) }
    }

    @Test
    fun `a book shown as separate books knows its series, to be shown as one again`() = runTest {
        wholeLibraryFromServer(potter(1), potter(2))
        every { audiobookEntryDao.separateBooks() } returns flowOf(listOf(potterKey))

        val viewModel = createViewModel()
        viewModel.itemNamesShouldBecome(listOf("HP1", "HP2"))

        viewModel.items.value.map { (it as LibraryItem.AudiobookBundle).seriesKey }.distinct() shouldBe listOf(potterKey)
    }

    private companion object {
        /** How long a load started on the IO thread may take to be seen. */
        const val SETTLE_MS = 5_000L
    }
}
