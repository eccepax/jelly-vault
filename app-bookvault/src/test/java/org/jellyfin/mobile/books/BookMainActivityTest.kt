package org.jellyfin.mobile.books

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.app.EpubFontFamily
import org.jellyfin.mobile.books.app.LibraryViewMode
import org.jellyfin.mobile.books.library.BookLibraryViewModel
import org.jellyfin.mobile.books.library.BookSortOption
import org.jellyfin.mobile.books.library.ProgressFilter
import org.jellyfin.mobile.books.reader.BookReaderViewModel
import org.jellyfin.mobile.books.reader.BookType
import org.jellyfin.mobile.books.settings.BookSettingsViewModel
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.ServerState
import org.jellyfin.mobile.common.app.ApiClientController
import org.jellyfin.mobile.common.data.entity.ServerEntity
import org.jellyfin.mobile.common.setup.LoginViewModel
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.testing.downloads.FakeDownloadSettings
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import org.jellyfin.mobile.common.R as CommonR

/**
 * BookVault's one activity: sign-in, then the library with the reader and settings over it.
 * Rotating on a non-Library screen (reader, settings) must not send the user back to the Library,
 * though [MainViewModel.serverState] replays its cached value to every recreated activity.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BookMainActivityTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val mainViewModel = mockk<MainViewModel>(relaxed = true)
    private val appPreferences = mockk<BookAppPreferences>(relaxed = true)
    private val libraryViewModel = mockk<BookLibraryViewModel>(relaxed = true)
    private val readerViewModel = mockk<BookReaderViewModel>(relaxed = true)
    private val dune = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.BOOK, name = "Dune")
    private val serverStateFlow = MutableStateFlow<ServerState>(
        ServerState.Available(ServerEntity(hostname = "jellyfin.example.com")),
    )

    @Before
    fun setUp() {
        every { mainViewModel.serverState } returns serverStateFlow
        every { appPreferences.currentUserId } returns 1L
        every { appPreferences.getLibraryViewMode(any()) } returns LibraryViewMode.LIST
        every { appPreferences.epubFontScale } returns 1.0f
        every { appPreferences.epubFontFamily } returns EpubFontFamily.SERIF
        every { appPreferences.epubLineSpacing } returns 1.0f
        every { libraryViewModel.filteredBooks } returns MutableStateFlow(listOf(dune))
        every { libraryViewModel.loading } returns MutableStateFlow(false)
        every { libraryViewModel.error } returns MutableStateFlow(null)
        every { libraryViewModel.downloadedIds } returns MutableStateFlow(emptySet())
        every { libraryViewModel.showDownloadedOnly } returns MutableStateFlow(false)
        every { libraryViewModel.searchQuery } returns MutableStateFlow("")
        every { libraryViewModel.sortOption } returns MutableStateFlow(BookSortOption.TITLE)
        every { libraryViewModel.progressFilter } returns MutableStateFlow(ProgressFilter.ALL)
        every { libraryViewModel.tagFilter } returns MutableStateFlow(null)
        every { readerViewModel.bookType } returns MutableStateFlow(BookType.EPUB)
        every { readerViewModel.loading } returns MutableStateFlow(false)
        every { readerViewModel.currentPage } returns MutableStateFlow(6)
        every { readerViewModel.totalPages } returns MutableStateFlow(19)
        every { readerViewModel.error } returns MutableStateFlow(null)
        every { readerViewModel.nightMode } returns MutableStateFlow(false)
        every { readerViewModel.bookmarks } returns MutableStateFlow(emptyList())
        every { readerViewModel.chosenTocEntry } returns MutableStateFlow(null)
        every { readerViewModel.tocJumps } returns MutableSharedFlow()
        every { readerViewModel.getChapterTitle(6) } returns "Introduction"

        startKoin {
            modules(
                module {
                    viewModel { mainViewModel }
                    viewModel { libraryViewModel }
                    viewModel { readerViewModel }
                    viewModel {
                        BookSettingsViewModel(
                            appPreferences,
                            FakeDownloadSettings(),
                            {},
                            ClientInfo("BookVault", "9.9.9"),
                        )
                    }
                    single { appPreferences }
                    // The connect screen resolves it directly via Koin
                    single { mockk<ApiClientController>(relaxed = true) }
                    // The sign-in screen's
                    viewModel { LoginViewModel(mockk(), mockk(), mockk(relaxed = true), mockk(relaxed = true)) }
                    single { VaultBrand.BookVault }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun launch() = ActivityScenario.launch(BookMainActivity::class.java)

    private fun openSettings() {
        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.vault_more_options)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.settings)).performClick()
    }

    @Test
    fun `fresh launch with an available server and a signed-in user shows the Library`() {
        launch().use {
            composeRule.onNodeWithText("Dune").assertIsDisplayed()
        }
    }

    @Test
    fun `signing in shows the Library`() {
        every { appPreferences.currentUserId } returns null

        launch().use {
            composeRule.onNodeWithText("Dune").assertDoesNotExist()

            // A completed sign-in: the user is saved and the ViewModel emits a fresh Available,
            // as the sign-in screen's `mainViewModel.refreshState()` does.
            every { appPreferences.currentUserId } returns 1L
            serverStateFlow.value = ServerState.Available(ServerEntity(hostname = "jellyfin.example.com"))

            composeRule.onNodeWithText("Dune").assertIsDisplayed()
        }
    }

    @Test
    fun `rotating on the settings stays on the settings, and back still returns to the Library`() {
        launch().use { scenario ->
            openSettings()
            composeRule.onNodeWithText("BookVault 9.9.9").assertExists()

            scenario.recreate()

            composeRule.onNodeWithText("BookVault 9.9.9").assertExists()
            composeRule.onNodeWithContentDescription(context.getString(CommonR.string.vault_back)).performClick()
            composeRule.onNodeWithText("Dune").assertIsDisplayed()
        }
    }

    @Test
    fun `rotating in a book stays in it, at its page`() {
        launch().use { scenario ->
            composeRule.onNodeWithText("Dune").performClick()
            composeRule.onNodeWithText("Introduction").assertIsDisplayed()

            scenario.recreate()

            composeRule.onNodeWithText("Introduction").assertIsDisplayed()
            composeRule.onNodeWithText("Chapter 7 of 19").assertIsDisplayed()
        }
    }
}
