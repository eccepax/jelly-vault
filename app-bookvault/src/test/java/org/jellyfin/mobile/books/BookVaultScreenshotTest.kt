package org.jellyfin.mobile.books

import android.app.Application
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.app.EpubFontFamily
import org.jellyfin.mobile.books.app.LibraryViewMode
import org.jellyfin.mobile.books.library.BookLibraryRoute
import org.jellyfin.mobile.books.library.BookLibraryViewModel
import org.jellyfin.mobile.books.library.BookSortOption
import org.jellyfin.mobile.books.library.ProgressFilter
import org.jellyfin.mobile.books.reader.BookReaderRoute
import org.jellyfin.mobile.books.reader.BookReaderViewModel
import org.jellyfin.mobile.books.reader.BookType
import org.jellyfin.mobile.books.settings.BookSettingsRoute
import org.jellyfin.mobile.books.settings.BookSettingsViewModel
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.testing.downloads.FakeDownloadSettings
import org.jellyfin.mobile.testsupport.PHONE_QUALIFIERS
import org.jellyfin.mobile.testsupport.SCREENSHOT_SDK
import org.jellyfin.mobile.testsupport.captureWithSystemBars
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.UUID

/** BookVault's main screens on a phone with 3-button navigation; see [captureWithSystemBars]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [SCREENSHOT_SDK], qualifiers = PHONE_QUALIFIERS)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookVaultScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val preferences = mockk<BookAppPreferences>(relaxed = true).apply {
        every { getLibraryViewMode(any()) } returns LibraryViewMode.LIST
        every { epubFontScale } returns 1.0f
        every { epubFontFamily } returns EpubFontFamily.SERIF
        every { epubLineSpacing } returns 1.0f
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
        capture("library") {
            BookLibraryRoute(onOpenBook = {}, onOpenSettings = {}, viewModel = library, preferences = preferences)
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
            every { chosenTocEntry } returns MutableStateFlow(null)
            every { getChapterTitle(6) } returns "Introduction"
        }
        capture("reader-scroll-mode") {
            BookReaderRoute(bookId = "book-1", onBack = {}, viewModel = reader, appPreferences = preferences)
        }
    }

    @Test
    fun settings() {
        val settings = BookSettingsViewModel(
            preferences,
            FakeDownloadSettings().apply { bytesUsed.value = 32_400_000L },
            signOut = {},
            app = ClientInfo("BookVault", "1.0.0"),
        )

        capture("settings") { BookSettingsRoute(onBack = {}, onSignedOut = {}, viewModel = settings) }
    }

    private fun capture(name: String, screen: @Composable () -> Unit) {
        composeRule.setContent { AppTheme(VaultBrand.BookVault, content = screen) }
        composeRule.waitForIdle()
        composeRule.activity.findViewById<View>(android.R.id.content).captureWithSystemBars(name)
    }
}
