package org.jellyfin.mobile.books.reader

import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Looper
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.jellyfin.mobile.common.R as CommonR

/**
 * Tests two easy-to-silently-break behaviors: the reading position must be saved when the reader
 * leaves the screen, and the night-mode toggle must actually reach the ViewModel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BookReaderFragmentTest {

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

    private fun args() = Bundle().apply { putString(BookReaderFragment.ARG_BOOK_ITEM_ID, "book-1") }

    @Test
    fun `leaving the reader reports the current playback position`() {
        launchFragmentInContainer<BookReaderFragment>(args(), themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.moveToState(Lifecycle.State.CREATED) // RESUMED -> CREATED crosses onStop()
        }

        verify { readerViewModel.reportPlaybackStopped() }
    }

    @Test
    fun `jumping to a page via setPage moves the displayed EPUB page even without a swipe`() {
        // Regression test: TOC/bookmark jumps and the PDF page-jump dialog all call
        // viewModel.setPage(...) directly — that has no path back to the ViewPager2 unless
        // something observes currentPage and moves it explicitly (see observeCurrentPage).
        every { readerViewModel.bookType } returns MutableStateFlow(BookType.EPUB)
        every { readerViewModel.totalPages } returns MutableStateFlow(5)

        lateinit var pager: ViewPager2
        launchFragmentInContainer<BookReaderFragment>(args(), themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment -> pager = fragment.view!!.findViewById(R.id.view_pager) }
            scenario.onFragment { currentPageFlow.value = 3 } // simulates a TOC/bookmark jump, not a swipe
        }

        pager.currentItem shouldBe 3
    }

    @Test
    fun `the night mode menu action toggles night mode on the view model`() {
        launchFragmentInContainer<BookReaderFragment>(args(), themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.view!!.findViewById<Toolbar>(R.id.toolbar)
                    .menu.performIdentifierAction(R.id.action_night_mode, 0)
            }
        }

        verify { readerViewModel.toggleNightMode() }
    }

    @Test
    fun `the text settings menu action opens the settings chooser dialog`() {
        launchFragmentInContainer<BookReaderFragment>(args(), themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.view!!.findViewById<Toolbar>(R.id.toolbar)
                    .menu.performIdentifierAction(R.id.action_text_settings, 0)
            }
        }

        ShadowDialog.getLatestDialog() shouldNotBe null
    }

    @Test
    fun `choosing a font size preset persists it and does not change the current page`() {
        currentPageFlow.value = 3

        launchFragmentInContainer<BookReaderFragment>(args(), themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment ->
                // Calls the dialog builder directly (marked internal for this) rather than routing
                // through the "Font size" chooser-dialog item click first — the chooser's own
                // setItems dialog works fine with Robolectric's ShadowDialog#clickOnText (see the
                // "opens the settings chooser dialog" test), but chaining straight into a SECOND,
                // setSingleChoiceItems-based dialog from within that click callback hits a
                // ShadowDialog NPE unrelated to this feature — a Robolectric/nested-dialog quirk,
                // not a production bug. Exercising this dialog directly avoids it.
                fragment.dialogs.showFontSizeDialog()
                shadowOf(Looper.getMainLooper()).idle()
                // setSingleChoiceItems' internal CheckedTextView rows also confuse
                // ShadowDialog#clickOnText itself (NPE deep in its shadow extraction) — drive the
                // ListView's item-click listener directly instead, the same mechanism a real touch
                // triggers.
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                val index = EPUB_FONT_SCALE_PRESETS.indexOf(1.25f)
                dialog.listView.performItemClick(dialog.listView.getChildAt(index), index, index.toLong())
            }
        }

        verify { appPreferences.epubFontScale = 1.25f }
        currentPageFlow.value shouldBe 3
    }

    // --- table of contents / page jump ------------------------------------------------------------

    @Test
    fun `the contents action shows the chapter list for an EPUB book and jumping to one calls setPage`() {
        every { readerViewModel.bookType } returns MutableStateFlow(BookType.EPUB)
        every { readerViewModel.getTableOfContents() } returns listOf(
            EpubTocEntry("Prologue", 0),
            EpubTocEntry("Chapter One", 1),
        )

        launchFragmentInContainer<BookReaderFragment>(args(), themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.view!!.findViewById<Toolbar>(R.id.toolbar)
                    .menu.performIdentifierAction(R.id.action_contents, 0)
                shadowOf(Looper.getMainLooper()).idle()
                // ShadowDialog#clickOnText only reliably finds the first ListView row under
                // Robolectric (later rows aren't always measured) — drive the item-click listener
                // directly by index instead, same mechanism a real touch triggers.
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                dialog.listView.performItemClick(dialog.listView.getChildAt(1), 1, 1L)
            }
        }

        verify { readerViewModel.setPage(1) }
    }

    @Test
    fun `the contents action shows a page-jump dialog for a PDF book`() {
        every { readerViewModel.bookType } returns MutableStateFlow(BookType.PDF)
        every { readerViewModel.totalPages } returns MutableStateFlow(180)

        launchFragmentInContainer<BookReaderFragment>(args(), themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.view!!.findViewById<Toolbar>(R.id.toolbar)
                    .menu.performIdentifierAction(R.id.action_contents, 0)
            }
        }

        ShadowDialog.getLatestDialog() shouldNotBe null
    }

    @Test
    fun `entering a valid page number in the PDF jump dialog calls setPage with a zero-based index`() {
        every { readerViewModel.bookType } returns MutableStateFlow(BookType.PDF)
        every { readerViewModel.totalPages } returns MutableStateFlow(180)

        launchFragmentInContainer<BookReaderFragment>(args(), themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.view!!.findViewById<Toolbar>(R.id.toolbar)
                    .menu.performIdentifierAction(R.id.action_contents, 0)
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                dialog.findViewById<EditText>(R.id.dialog_page_input)!!.setText("42")
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
        }

        verify { readerViewModel.setPage(41) }
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
        launchFragmentInContainer<BookReaderFragment>(args(), themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment -> recyclerView = fragment.view!!.findViewById(R.id.recycler_view) }
            shadowOf(Looper.getMainLooper()).idle()

            scenario.onFragment { currentPageFlow.value = 9 } // simulates a TOC/bookmark/page-jump dialog
            // Simulates a relayout landing well after the jump (a late-arriving bitmap), still
            // with no real user interaction in between.
            scenario.onFragment { recyclerView.scrollBy(0, 5) }
        }

        verify(exactly = 0) { readerViewModel.setPage(any()) }
    }

    // --- bookmarks ----------------------------------------------------------------------------------

    @Test
    fun `the add-bookmark action prompts for a note and calls addBookmark with the entered text`() {
        launchFragmentInContainer<BookReaderFragment>(args(), themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.view!!.findViewById<Toolbar>(R.id.toolbar)
                    .menu.performIdentifierAction(R.id.action_add_bookmark, 0)
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                dialog.findViewById<EditText>(R.id.dialog_bookmark_note_input)!!.setText("Great line here")
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
        }

        verify { readerViewModel.addBookmark("Great line here") }
    }

    @Test
    fun `skipping the note in the add-bookmark prompt still adds a bookmark`() {
        launchFragmentInContainer<BookReaderFragment>(args(), themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.view!!.findViewById<Toolbar>(R.id.toolbar)
                    .menu.performIdentifierAction(R.id.action_add_bookmark, 0)
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
        }

        verify { readerViewModel.addBookmark("") }
    }

    @Test
    fun `tapping a saved bookmark and choosing jump calls jumpToBookmark`() {
        val bookmark = BookBookmarkEntity(
            id = 1L,
            bookItemId = "book-1",
            pageIndex = 2,
            label = "Chapter 3",
            note = null,
            createdAt = 0L,
        )
        every { readerViewModel.bookmarks } returns MutableStateFlow(listOf(bookmark))

        launchFragmentInContainer<BookReaderFragment>(args(), themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.view!!.findViewById<Toolbar>(R.id.toolbar)
                    .menu.performIdentifierAction(R.id.action_bookmarks, 0)
                shadowOf(Looper.getMainLooper()).idle()
                val listDialog = ShadowDialog.getLatestDialog() as AlertDialog
                listDialog.listView.performItemClick(listDialog.listView.getChildAt(0), 0, 0L)
                shadowOf(Looper.getMainLooper()).idle()
                // Per-bookmark action menu: "Jump to bookmark" (index 0) / "Delete" (index 1).
                val actionDialog = ShadowDialog.getLatestDialog() as AlertDialog
                actionDialog.listView.performItemClick(actionDialog.listView.getChildAt(0), 0, 0L)
            }
        }

        verify { readerViewModel.jumpToBookmark(bookmark) }
    }

    @Test
    fun `tapping a saved bookmark and choosing delete calls deleteBookmark`() {
        val bookmark = BookBookmarkEntity(
            id = 1L,
            bookItemId = "book-1",
            pageIndex = 2,
            label = "Chapter 3",
            note = null,
            createdAt = 0L,
        )
        every { readerViewModel.bookmarks } returns MutableStateFlow(listOf(bookmark))

        launchFragmentInContainer<BookReaderFragment>(args(), themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.view!!.findViewById<Toolbar>(R.id.toolbar)
                    .menu.performIdentifierAction(R.id.action_bookmarks, 0)
                shadowOf(Looper.getMainLooper()).idle()
                val listDialog = ShadowDialog.getLatestDialog() as AlertDialog
                listDialog.listView.performItemClick(listDialog.listView.getChildAt(0), 0, 0L)
                shadowOf(Looper.getMainLooper()).idle()
                val actionDialog = ShadowDialog.getLatestDialog() as AlertDialog
                actionDialog.listView.performItemClick(actionDialog.listView.getChildAt(1), 1, 1L)
            }
        }

        verify { readerViewModel.deleteBookmark(1L) }
    }
}
