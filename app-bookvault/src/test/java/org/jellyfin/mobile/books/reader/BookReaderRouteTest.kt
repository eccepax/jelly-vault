package org.jellyfin.mobile.books.reader

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.app.EpubFontFamily
import org.jellyfin.mobile.books.data.entity.BookBookmarkEntity
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.jellyfin.mobile.common.R as CommonR

/**
 * The reader screen wired to its ViewModel: each action reaches it, the reading position is sent
 * on putting the app away, and jumps that don't come from a swipe still move the pages (the
 * screen's own behavior is in [BookReaderScreenTest]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BookReaderRouteTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val readerViewModel = mockk<BookReaderViewModel>(relaxed = true)
    private val appPreferences = mockk<BookAppPreferences>(relaxed = true)
    private val currentPageFlow = MutableStateFlow(0)
    private var backs = 0

    @Before
    fun setUp() {
        every { readerViewModel.bookType } returns MutableStateFlow(BookType.UNKNOWN)
        every { readerViewModel.loading } returns MutableStateFlow(false)
        every { readerViewModel.currentPage } returns currentPageFlow
        every { readerViewModel.totalPages } returns MutableStateFlow(0)
        every { readerViewModel.error } returns MutableStateFlow(null)
        every { readerViewModel.nightMode } returns MutableStateFlow(false)
        every { readerViewModel.bookmarks } returns MutableStateFlow(emptyList())
        every { readerViewModel.chosenTocEntry } returns MutableStateFlow(null)
        every { readerViewModel.tocJumps } returns MutableSharedFlow()
        every { readerViewModel.getTableOfContents() } returns emptyList()
        every { readerViewModel.pageAspectRatio(any()) } returns A4_ASPECT_RATIO
        coEvery { readerViewModel.renderPage(any()) } returns null

        every { appPreferences.epubFontScale } returns 1.0f
        every { appPreferences.epubFontFamily } returns EpubFontFamily.SERIF
        every { appPreferences.epubLineSpacing } returns 1.0f
    }

    private fun show() {
        composeRule.setContent {
            AppTheme(VaultBrand.BookVault) {
                BookReaderRoute(
                    bookId = "book-1",
                    onBack = { backs++ },
                    viewModel = readerViewModel,
                    appPreferences = appPreferences,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun <T : View> view(id: Int): T = composeRule.activity.findViewById(id)

    private fun epub(chapters: Int = 19) {
        every { readerViewModel.bookType } returns MutableStateFlow(BookType.EPUB)
        every { readerViewModel.totalPages } returns MutableStateFlow(chapters)
    }

    private fun pdf(pages: Int = 20) {
        every { readerViewModel.bookType } returns MutableStateFlow(BookType.PDF)
        every { readerViewModel.totalPages } returns MutableStateFlow(pages)
    }

    private fun string(resId: Int) = ApplicationProvider.getApplicationContext<Application>().getString(resId)

    private fun openMenuItem(resId: Int) {
        composeRule.onNodeWithContentDescription(string(CommonR.string.vault_more_options)).performClick()
        composeRule.onNodeWithText(string(resId)).performClick()
    }

    @Test
    fun `the book is opened, in night mode only if the app's theme is dark`() {
        show()

        verify(exactly = 1) { readerViewModel.loadBook("book-1", false) }
    }

    @Test
    fun `putting the app away sends the reading place`() {
        show()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED) // crosses onStop()

        verify { readerViewModel.sendProgress() }
    }

    @Test
    fun `back goes back`() {
        show()

        composeRule.onNodeWithContentDescription(string(CommonR.string.vault_back)).performClick()

        backs shouldBe 1
    }

    @Test
    fun `the chapter bar names the chapter being read`() {
        epub()
        every { readerViewModel.getChapterTitle(6) } returns "Introduction"
        currentPageFlow.value = 6

        show()

        composeRule.onNodeWithText("Introduction").assertExists()
        composeRule.onNodeWithText("Chapter 7 of 19").assertExists()
    }

    @Test
    fun `the next chapter arrow moves to the next chapter`() {
        epub()
        currentPageFlow.value = 6

        show()
        composeRule.onNodeWithContentDescription(string(R.string.chapter_next)).performClick()

        verify { readerViewModel.setPage(7) }
    }

    @Test
    fun `the EPUB chapter buttons stay above the system navigation bar`() {
        // The app draws edge-to-edge; with 3-button navigation the bar would otherwise cover the
        // Previous/Next chapter buttons, leaving no way past a chapter with nothing to scroll.
        epub(chapters = 5)

        show()
        composeRule.runOnIdle {
            val bars = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, NAV_BAR_PX))
                .build()
            ViewCompat.dispatchApplyWindowInsets(view(android.R.id.content), bars)
        }

        val screenHeight = composeRule.onRoot().fetchSemanticsNode().size.height
        val next = composeRule.onNodeWithContentDescription(string(R.string.chapter_next)).fetchSemanticsNode()
        next.boundsInRoot.bottom shouldBeLessThanOrEqual (screenHeight - NAV_BAR_PX).toFloat()
    }

    @Test
    fun `the pages fill the screen between the bars`() {
        pdf()

        show()

        val root = composeRule.onRoot().fetchSemanticsNode().size
        val pages = composeRule.onNodeWithTag(READER_PAGES).fetchSemanticsNode().size
        pages.width shouldBe root.width
        pages.height shouldBeGreaterThan root.height / 2
    }

    private fun zoomScale(): Float =
        composeRule.onNodeWithTag(READER_PAGES).fetchSemanticsNode().config[PageZoomScale]

    @Test
    fun `a double tap zooms a PDF's pages in, and another zooms them back out`() {
        pdf()

        show()
        zoomScale() shouldBe 1f
        composeRule.onNodeWithTag(READER_PAGES).performTouchInput { doubleClick() }
        zoomScale() shouldBe PageZoom.DOUBLE_TAP_SCALE

        composeRule.onNodeWithTag(READER_PAGES).performTouchInput { doubleClick() }
        zoomScale() shouldBe 1f
    }

    @Test
    fun `a pinch zooms a PDF's pages in`() {
        pdf()

        show()
        composeRule.onNodeWithTag(READER_PAGES).performTouchInput {
            pinch(center - Offset(50f, 0f), centerLeft, center + Offset(50f, 0f), centerRight)
        }

        zoomScale() shouldBeGreaterThan 1.5f
    }

    @Test
    fun `an EPUB's pages don't zoom, since its text sizes itself`() {
        epub()

        show()
        composeRule.onNodeWithTag(READER_PAGES).performTouchInput { doubleClick() }

        zoomScale() shouldBe 1f
    }

    @Test
    fun `read as a scroll, a zoomed-in PDF still scrolls up and down, and the place follows`() {
        pdf()

        show()
        composeRule.onNodeWithTag(READER_PAGES).performTouchInput { doubleClick() }
        composeRule.onNodeWithTag(READER_PAGES).performTouchInput { swipeUp() }
        composeRule.waitForIdle()

        verify { readerViewModel.setPage(match { it > 0 }) }
    }

    @Test
    fun `turning to another page (page mode) zooms back out`() {
        pdf()

        show()
        composeRule.onNodeWithContentDescription(string(R.string.reader_page_mode)).performClick()
        composeRule.onNodeWithTag(READER_PAGES).performTouchInput { doubleClick() }
        zoomScale() shouldBe PageZoom.DOUBLE_TAP_SCALE
        composeRule.runOnIdle { currentPageFlow.value = 3 }
        composeRule.waitForIdle()

        zoomScale() shouldBe 1f
    }

    @Test
    fun `jumping to a page via setPage moves the displayed EPUB page even without a swipe`() {
        // Regression test: TOC/bookmark jumps and the PDF page-jump dialog all call
        // viewModel.setPage(...) directly; that has no path back to the pager unless something
        // observes currentPage and moves it explicitly.
        epub(chapters = 5)

        show()
        composeRule.runOnIdle { currentPageFlow.value = 3 } // simulates a TOC/bookmark jump, not a swipe
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(readerPageTag(3)).assertIsDisplayed()
    }

    @Test
    fun `swiping to another page (page mode) moves the place there`() {
        pdf()

        show()
        composeRule.onNodeWithContentDescription(string(R.string.reader_page_mode)).performClick()
        composeRule.onNodeWithTag(READER_PAGES).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        verify { readerViewModel.setPage(1) }
    }

    @Test
    fun `a PDF opens as a scroll at the place it was left`() {
        pdf()
        currentPageFlow.value = 9

        show()

        composeRule.onNodeWithTag(readerPageTag(9)).assertIsDisplayed()
        verify(exactly = 0) { readerViewModel.setPage(any()) }
    }

    @Test
    fun `a programmatic PDF page jump is not overwritten by pages drawn after it`() {
        // The scroll's page pictures arrive after the jump. Each page has its size from the start, so
        // a picture arriving moves nothing; and only the reader's own scrolling moves the place.
        pdf()
        coEvery { readerViewModel.renderPage(any()) } returns Bitmap.createBitmap(200, 2000, Bitmap.Config.ARGB_8888)

        show()
        composeRule.runOnIdle { currentPageFlow.value = 9 } // simulates a TOC/bookmark/page-jump dialog
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(readerPageTag(9)).assertIsDisplayed()
        verify(exactly = 0) { readerViewModel.setPage(any()) }
    }

    @Test
    fun `the reading mode chosen survives a rotation`() {
        pdf()
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent {
            AppTheme(VaultBrand.BookVault) {
                BookReaderRoute(
                    bookId = "book-1",
                    onBack = {},
                    viewModel = readerViewModel,
                    appPreferences = appPreferences,
                )
            }
        }
        composeRule.onNodeWithContentDescription(string(R.string.reader_page_mode)).performClick()

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription(string(R.string.reader_scroll_mode)).assertExists()
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange)).assertExists()
    }

    @Test
    fun `the night mode button toggles night mode on the view model`() {
        show()
        composeRule.onNodeWithContentDescription(string(R.string.reader_night_mode_on)).performClick()

        verify { readerViewModel.toggleNightMode() }
    }

    @Test
    fun `choosing a font size saves it and does not change the current page`() {
        epub()
        currentPageFlow.value = 3

        show()
        openMenuItem(R.string.text_settings_title)
        composeRule.onNodeWithText(string(R.string.text_settings_font_size)).performClick()
        composeRule.onNodeWithText("125%").performClick()

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

        show()
        openMenuItem(R.string.contents_title)
        composeRule.onNodeWithText("Chapter One").performClick()

        verify { readerViewModel.openTocEntry(EpubTocEntry("Chapter One", 1)) }
    }

    @Test
    fun `entering a page number for a PDF goes to it, counting from zero`() {
        pdf(pages = 180)

        show()
        openMenuItem(R.string.contents_title)
        composeRule.onNodeWithText(string(R.string.go_to_page_hint)).performTextInput("42")
        composeRule.onNodeWithText(string(R.string.go)).performClick()

        verify { readerViewModel.setPage(41) }
    }

    @Test
    fun `adding a bookmark saves it with the note`() {
        show()
        openMenuItem(R.string.add_bookmark_action)
        composeRule.onNodeWithText(string(R.string.add_bookmark_note_hint)).performTextInput("Great line here")
        composeRule.onNodeWithText(string(R.string.bookmark_save)).performClick()

        verify { readerViewModel.addBookmark("Great line here") }
    }

    @Test
    fun `tapping a saved bookmark jumps to it`() {
        every { readerViewModel.bookmarks } returns MutableStateFlow(listOf(bookmark))

        show()
        openMenuItem(R.string.bookmarks_title)
        composeRule.onNodeWithText("Chapter 3").performClick()

        verify { readerViewModel.jumpToBookmark(bookmark) }
    }

    @Test
    fun `deleting a saved bookmark deletes it`() {
        every { readerViewModel.bookmarks } returns MutableStateFlow(listOf(bookmark))

        show()
        openMenuItem(R.string.bookmarks_title)
        composeRule.onNodeWithContentDescription(
            ApplicationProvider.getApplicationContext<Application>().getString(R.string.delete_bookmark, "Chapter 3"),
        ).performClick()

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
        const val A4_ASPECT_RATIO = 210f / 297f
    }
}
