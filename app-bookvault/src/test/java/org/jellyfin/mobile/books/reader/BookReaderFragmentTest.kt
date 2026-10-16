package org.jellyfin.mobile.books.reader

import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Looper
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.viewpager2.widget.ViewPager2
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.app.EpubFontFamily
import org.jellyfin.mobile.books.data.entity.BookBookmarkEntity
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
import org.jellyfin.mobile.common.R as CommonR

/**
 * The reader screen wired to its ViewModel: each action reaches it, the reading position is saved
 * on leaving, and jumps that don't come from a swipe still move the pages (the screen's own
 * behavior is in [BookReaderScreenTest]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BookReaderFragmentTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val readerViewModel = mockk<BookReaderViewModel>(relaxed = true)
    private val appPreferences = mockk<BookAppPreferences>(relaxed = true)
    private val currentPageFlow = MutableStateFlow(0)

    @Before
    fun setUp() {
        every { readerViewModel.bookType } returns MutableStateFlow(BookType.UNKNOWN)
        every { readerViewModel.loading } returns MutableStateFlow(false)
        every { readerViewModel.currentPage } returns currentPageFlow
        every { readerViewModel.totalPages } returns MutableStateFlow(0)
        every { readerViewModel.error } returns MutableStateFlow(null)
        every { readerViewModel.nightMode } returns MutableStateFlow(false)
        every { readerViewModel.bookmarks } returns MutableStateFlow(emptyList())
        every { readerViewModel.getTableOfContents() } returns emptyList()

        every { appPreferences.epubFontScale } returns 1.0f
        every { appPreferences.epubFontFamily } returns EpubFontFamily.SERIF
        every { appPreferences.epubLineSpacing } returns 1.0f

        startKoin {
            modules(
                module {
                    viewModel { readerViewModel }
                    single { appPreferences }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun launch() = launchFragmentInContainer<BookReaderFragment>(
        Bundle().apply { putString(BookReaderFragment.ARG_BOOK_ITEM_ID, "book-1") },
        themeResId = CommonR.style.AppTheme,
    )

    private fun epub(chapters: Int = 19) {
        every { readerViewModel.bookType } returns MutableStateFlow(BookType.EPUB)
        every { readerViewModel.totalPages } returns MutableStateFlow(chapters)
    }

    private fun string(resId: Int) = ApplicationProvider.getApplicationContext<Application>().getString(resId)

    private fun openMenuItem(resId: Int) {
        composeRule.onNodeWithContentDescription(string(CommonR.string.vault_more_options)).performClick()
        composeRule.onNodeWithText(string(resId)).performClick()
    }

    @Test
    fun `leaving the reader reports the current playback position`() {
        launch().use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED) // RESUMED -> CREATED crosses onStop()
        }

        verify { readerViewModel.reportPlaybackStopped() }
    }

    @Test
    fun `the chapter bar names the chapter being read`() {
        epub()
        every { readerViewModel.getChapterTitle(6) } returns "Introduction"
        currentPageFlow.value = 6

        launch().use {
            composeRule.onNodeWithText("Introduction").assertExists()
            composeRule.onNodeWithText("Chapter 7 of 19").assertExists()
        }
    }

    @Test
    fun `the next chapter arrow moves to the next chapter`() {
        epub()
        currentPageFlow.value = 6

        launch().use {
            composeRule.onNodeWithContentDescription(string(R.string.chapter_next)).performClick()
        }

        verify { readerViewModel.setPage(7) }
    }

    @Test
    fun `the EPUB chapter buttons stay above the system navigation bar`() {
        // The app draws edge-to-edge; with 3-button navigation the bar would otherwise cover the
        // Previous/Next chapter buttons, leaving no way past a chapter with nothing to scroll.
        epub(chapters = 5)

        launch().use { scenario ->
            scenario.onFragment { fragment ->
                val bars = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NAV_BAR_PX))
                    .build()
                ViewCompat.dispatchApplyWindowInsets(fragment.requireView(), bars)
            }
            shadowOf(Looper.getMainLooper()).idle()

            val screenHeight = composeRule.onRoot().fetchSemanticsNode().size.height
            val next = composeRule.onNodeWithContentDescription(string(R.string.chapter_next)).fetchSemanticsNode()
            next.boundsInRoot.bottom shouldBeLessThanOrEqual (screenHeight - NAV_BAR_PX).toFloat()
        }
    }

    @Test
    fun `the pages fill the screen between the bars`() {
        every { readerViewModel.bookType } returns MutableStateFlow(BookType.PDF)
        every { readerViewModel.totalPages } returns MutableStateFlow(20)

        launch().use { scenario ->
            shadowOf(Looper.getMainLooper()).idle()
            scenario.onFragment { fragment ->
                val root = fragment.requireView()
                val pages = root.findViewById<RecyclerView>(R.id.recycler_view)
                pages.width shouldBe root.width
                pages.height shouldBeGreaterThan root.height / 2
            }
        }
    }

    @Test
    fun `jumping to a page via setPage moves the displayed EPUB page even without a swipe`() {
        // Regression test: TOC/bookmark jumps and the PDF page-jump dialog all call
        // viewModel.setPage(...) directly — that has no path back to the ViewPager2 unless
        // something observes currentPage and moves it explicitly (see observeCurrentPage).
        epub(chapters = 5)

        lateinit var pager: ViewPager2
        launch().use { scenario ->
            scenario.onFragment { fragment -> pager = fragment.requireView().findViewById(R.id.view_pager) }
            scenario.onFragment { currentPageFlow.value = 3 } // simulates a TOC/bookmark jump, not a swipe
        }

        pager.currentItem shouldBe 3
    }

    @Test
    fun `a programmatic PDF page jump is not overwritten by later relayout-driven scroll tracking`() {
        // PdfScrollAdapter binds page bitmaps asynchronously, so pages around a jump target keep
        // changing height for several frames, and each relayout fires the scroll listener. Tracking
        // those would overwrite the jump target (jumping to page 10 could save page 3), so tracking
        // stays off until a real user drag; no timer is long enough to be reliable.
        every { readerViewModel.bookType } returns MutableStateFlow(BookType.PDF)
        every { readerViewModel.totalPages } returns MutableStateFlow(20)
        coEvery { readerViewModel.renderPage(any()) } returns Bitmap.createBitmap(200, 2000, Bitmap.Config.ARGB_8888)

        lateinit var recyclerView: RecyclerView
        launch().use { scenario ->
            scenario.onFragment { fragment -> recyclerView = fragment.requireView().findViewById(R.id.recycler_view) }
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onFragment { currentPageFlow.value = 9 } // simulates a TOC/bookmark/page-jump dialog
            // Simulates a relayout landing well after the jump (a late-arriving bitmap), still
            // with no real user interaction in between.
            scenario.onFragment { recyclerView.scrollBy(0, 5) }
        }

        verify(exactly = 0) { readerViewModel.setPage(any()) }
    }

    @Test
    fun `the night mode button toggles night mode on the view model`() {
        launch().use {
            composeRule.onNodeWithContentDescription(string(R.string.reader_night_mode_on)).performClick()
        }

        verify { readerViewModel.toggleNightMode() }
    }

    @Test
    fun `choosing a font size saves it and does not change the current page`() {
        currentPageFlow.value = 3

        launch().use {
            openMenuItem(R.string.text_settings_title)
            composeRule.onNodeWithText(string(R.string.text_settings_font_size)).performClick()
            composeRule.onNodeWithText("125%").performClick()
        }

        verify { appPreferences.epubFontScale = 1.25f }
        currentPageFlow.value shouldBe 3
    }

    @Test
    fun `choosing a chapter from an EPUB's contents goes to it`() {
        epub()
        every { readerViewModel.getTableOfContents() } returns listOf(
            EpubTocEntry("Prologue", 0),
            EpubTocEntry("Chapter One", 1),
        )

        launch().use {
            openMenuItem(R.string.contents_title)
            composeRule.onNodeWithText("Chapter One").performClick()
        }

        verify { readerViewModel.setPage(1) }
    }

    @Test
    fun `entering a page number for a PDF goes to it, counting from zero`() {
        every { readerViewModel.bookType } returns MutableStateFlow(BookType.PDF)
        every { readerViewModel.totalPages } returns MutableStateFlow(180)

        launch().use {
            openMenuItem(R.string.contents_title)
            composeRule.onNodeWithText(string(R.string.go_to_page_hint)).performTextInput("42")
            composeRule.onNodeWithText(string(R.string.go)).performClick()
        }

        verify { readerViewModel.setPage(41) }
    }

    @Test
    fun `adding a bookmark saves it with the note`() {
        launch().use {
            openMenuItem(R.string.add_bookmark_action)
            composeRule.onNodeWithText(string(R.string.add_bookmark_note_hint)).performTextInput("Great line here")
            composeRule.onNodeWithText(string(R.string.bookmark_save)).performClick()
        }

        verify { readerViewModel.addBookmark("Great line here") }
    }

    @Test
    fun `tapping a saved bookmark jumps to it`() {
        every { readerViewModel.bookmarks } returns MutableStateFlow(listOf(bookmark))

        launch().use {
            openMenuItem(R.string.bookmarks_title)
            composeRule.onNodeWithText("Chapter 3").performClick()
        }

        verify { readerViewModel.jumpToBookmark(bookmark) }
    }

    @Test
    fun `deleting a saved bookmark deletes it`() {
        every { readerViewModel.bookmarks } returns MutableStateFlow(listOf(bookmark))

        launch().use {
            openMenuItem(R.string.bookmarks_title)
            composeRule.onNodeWithContentDescription(
                ApplicationProvider.getApplicationContext<Application>().getString(
                    R.string.delete_bookmark,
                    "Chapter 3",
                ),
            ).performClick()
        }

        verify { readerViewModel.deleteBookmark(1L) }
    }

    private val bookmark = BookBookmarkEntity(
        id = 1L,
        bookItemId = "book-1",
        pageIndex = 2,
        label = "Chapter 3",
        note = null,
        createdAt = 0L,
    )

    private companion object {
        const val NAV_BAR_PX = 126
    }
}
