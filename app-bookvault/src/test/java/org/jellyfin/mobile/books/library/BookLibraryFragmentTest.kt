package org.jellyfin.mobile.books.library

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.app.LibraryViewMode
import org.jellyfin.mobile.common.ui.VaultTestTags
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.util.UUID
import org.jellyfin.mobile.common.R as CommonR

/** How [BookLibraryFragment] connects its screen to the library's view model and preferences. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class BookLibraryFragmentTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val dune = BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.BOOK, name = "Dune")
    private val libraryViewModel = mockk<BookLibraryViewModel>(relaxed = true)
    private val bookAppPreferences = mockk<BookAppPreferences>(relaxed = true)
    private val downloadedIds = MutableStateFlow<Set<String>>(emptySet())
    private val tagFilter = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val showDownloadedOnly = MutableStateFlow(false)

    @Before
    fun setUp() {
        every { libraryViewModel.filteredBooks } returns MutableStateFlow(listOf(dune))
        every { libraryViewModel.loading } returns MutableStateFlow(false)
        every { libraryViewModel.error } returns MutableStateFlow(null)
        every { libraryViewModel.downloadedIds } returns downloadedIds
        every { libraryViewModel.showDownloadedOnly } returns showDownloadedOnly
        every { libraryViewModel.tagFilter } returns tagFilter
        every { libraryViewModel.searchQuery } returns searchQuery
        every { libraryViewModel.sortOption } returns MutableStateFlow(BookSortOption.TITLE)
        every { libraryViewModel.progressFilter } returns MutableStateFlow(ProgressFilter.ALL)
        every { libraryViewModel.getAvailableTags() } returns listOf("Fantasy", "Sci-Fi")
        every { bookAppPreferences.getLibraryViewMode(any()) } returns LibraryViewMode.LIST

        startKoin {
            modules(
                module {
                    single { bookAppPreferences }
                    viewModel { libraryViewModel }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun launch() = launchFragmentInContainer<BookLibraryFragment>(themeResId = CommonR.style.AppTheme)

    private fun string(resId: Int): String =
        ApplicationProvider.getApplicationContext<Application>().getString(resId)

    @Test
    fun `the book list ends above the system navigation bar`() {
        launch().use { scenario ->
            scenario.onFragment { fragment ->
                val bars = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NAV_BAR_PX))
                    .build()
                ViewCompat.dispatchApplyWindowInsets(fragment.requireView(), bars)
            }
            shadowOf(Looper.getMainLooper()).idle()

            val screenHeight = composeRule.onRoot().fetchSemanticsNode().size.height
            val list = composeRule.onNodeWithTag(VaultTestTags.LIBRARY_LIST).fetchSemanticsNode()
            list.boundsInRoot.bottom shouldBeLessThanOrEqual (screenHeight - NAV_BAR_PX).toFloat()
        }
    }

    @Test
    fun `the library opens in the view it was left in, and switching saves the other`() {
        every { bookAppPreferences.getLibraryViewMode(any()) } returns LibraryViewMode.SHELF

        launch().use {
            composeRule.onNodeWithTag(VaultTestTags.LIBRARY_GRID).assertExists()
            composeRule.onNodeWithContentDescription(string(R.string.action_view_list)).performClick()
            composeRule.onNodeWithTag(VaultTestTags.LIBRARY_LIST).assertExists()
        }

        verify { bookAppPreferences.setLibraryViewMode(any(), LibraryViewMode.LIST) }
    }

    @Test
    fun `long-pressing a book that isn't downloaded starts the download and says so`() {
        launch().use {
            composeRule.onNodeWithText("Dune").performTouchInput { longClick() }
            composeRule.waitForIdle()
        }

        verify { libraryViewModel.downloadBook(dune) }
        ShadowToast.getTextOfLatestToast() shouldBe string(R.string.downloading_book)
    }

    @Test
    fun `removing a downloaded book's download, once confirmed, deletes it`() {
        downloadedIds.value = setOf(dune.id.toString())

        launch().use {
            composeRule.onNodeWithText("Dune").performTouchInput { longClick() }
            composeRule.onNodeWithText(string(R.string.remove_download)).performClick()
        }

        verify { libraryViewModel.deleteDownload(dune) }
    }

    @Test
    fun `search narrows the library as you type`() {
        launch().use {
            composeRule.onNodeWithContentDescription(string(R.string.action_search)).performClick()
            composeRule.onNodeWithTag(VaultTestTags.SEARCH_FIELD).performTextInput("Dun")
        }

        searchQuery.value shouldBe "Dun"
    }

    @Test
    fun `choosing a tag narrows the library to it`() {
        launch().use {
            composeRule.onNodeWithContentDescription(string(CommonR.string.vault_more_options)).performClick()
            composeRule.onNodeWithText(string(R.string.filter_tag)).performClick()
            composeRule.onNodeWithText("Fantasy").performClick()
        }

        tagFilter.value shouldBe "Fantasy"
    }

    @Test
    fun `downloaded only switches the library to downloaded books`() {
        launch().use {
            composeRule.onNodeWithContentDescription(string(CommonR.string.vault_more_options)).performClick()
            composeRule.onNodeWithText(string(R.string.filter_downloaded_only)).performClick()
        }

        showDownloadedOnly.value shouldBe true
    }

    private companion object {
        const val NAV_BAR_PX = 126
    }
}
