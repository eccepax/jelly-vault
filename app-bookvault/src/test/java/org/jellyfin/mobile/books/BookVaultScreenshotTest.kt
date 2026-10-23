package org.jellyfin.mobile.books

import android.app.Application
import android.os.Bundle
import androidx.fragment.app.testing.launchFragmentInContainer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.app.EpubFontFamily
import org.jellyfin.mobile.books.app.LibraryViewMode
import org.jellyfin.mobile.books.library.BookLibraryFragment
import org.jellyfin.mobile.books.library.BookLibraryViewModel
import org.jellyfin.mobile.books.library.BookSortOption
import org.jellyfin.mobile.books.library.ProgressFilter
import org.jellyfin.mobile.books.reader.BookReaderFragment
import org.jellyfin.mobile.books.reader.BookReaderViewModel
import org.jellyfin.mobile.books.reader.BookType
import org.jellyfin.mobile.books.settings.BookSettingsFragment
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.downloads.DownloadSettings
import org.jellyfin.mobile.testing.downloads.FakeDownloadSettings
import org.jellyfin.mobile.testsupport.PHONE_QUALIFIERS
import org.jellyfin.mobile.testsupport.SCREENSHOT_SDK
import org.jellyfin.mobile.testsupport.captureWithSystemBars
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID
import org.jellyfin.mobile.common.R as CommonR

/** BookVault's main screens on a phone with 3-button navigation; see [captureWithSystemBars]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [SCREENSHOT_SDK], qualifiers = PHONE_QUALIFIERS)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookVaultScreenshotTest {

    private val preferences = mockk<BookAppPreferences>(relaxed = true).apply {
        every { getLibraryViewMode(any()) } returns LibraryViewMode.LIST
        every { epubFontScale } returns 1.0f
        every { epubFontFamily } returns EpubFontFamily.SERIF
        every { epubLineSpacing } returns 1.0f
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun library() {
        val books = listOf(
            "A People's History of the United States", "Braiding Sweetgrass", "Caste", "Dune",
            "Educated", "Facing East from Indian Country", "Giovanni's Room", "Homegoing",
            "Invisible Man", "Just Mercy", "Kindred", "The Left Hand of Darkness",
            "Middlemarch", "The Palestine Laboratory",
        ).map { BaseItemDto(id = UUID.nameUUIDFromBytes(it.toByteArray()), name = it, type = BaseItemKind.BOOK) }
        val library = mockk<BookLibraryViewModel>(relaxed = true).apply {
            every { filteredBooks } returns MutableStateFlow(books)
            every { loading } returns MutableStateFlow(false)
            every { error } returns MutableStateFlow(null)
            every { downloadedIds } returns MutableStateFlow(setOf(books.first { it.name == "Dune" }.id.toString()))
            every { showDownloadedOnly } returns MutableStateFlow(false)
            every { searchQuery } returns MutableStateFlow("")
            every { sortOption } returns MutableStateFlow(BookSortOption.TITLE)
            every { progressFilter } returns MutableStateFlow(ProgressFilter.ALL)
            every { tagFilter } returns MutableStateFlow(null)
            every { getAvailableTags() } returns emptyList()
        }
        startKoin {
            modules(
                module {
                    single { preferences }
                    viewModel { library }
                },
            )
        }

        launchFragmentInContainer<BookLibraryFragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { it.requireView().captureWithSystemBars("library") }
        }
    }

    @Test
    fun `reader in scroll mode`() {
        val reader = mockk<BookReaderViewModel>(relaxed = true).apply {
            every { bookType } returns MutableStateFlow(BookType.EPUB)
            every { loading } returns MutableStateFlow(false)
            every { currentPage } returns MutableStateFlow(6)
            every { totalPages } returns MutableStateFlow(19)
            every { error } returns MutableStateFlow(null)
            every { nightMode } returns MutableStateFlow(true)
            every { bookmarks } returns MutableStateFlow(emptyList())
            every { getChapterTitle(6) } returns "Introduction"
        }
        startKoin {
            modules(
                module {
                    single { preferences }
                    viewModel { reader }
                },
            )
        }

        val args = Bundle().apply { putString(BookReaderFragment.ARG_BOOK_ITEM_ID, "book-1") }
        launchFragmentInContainer<BookReaderFragment>(args, themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { it.requireView().captureWithSystemBars("reader-scroll-mode") }
        }
    }

    @Test
    fun settings() {
        startKoin {
            modules(
                module {
                    single { preferences }
                    single { ClientInfo("BookVault", "1.0.0") }
                    single<DownloadSettings> { FakeDownloadSettings().apply { bytesUsed.value = 32_400_000L } }
                    viewModel { mockk<MainViewModel>(relaxed = true) }
                },
            )
        }

        launchFragmentInContainer<BookSettingsFragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { it.requireView().captureWithSystemBars("settings") }
        }
    }
}
