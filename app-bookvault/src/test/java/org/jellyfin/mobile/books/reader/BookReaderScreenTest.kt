package org.jellyfin.mobile.books.reader

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.data.entity.BookBookmarkEntity
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.jellyfin.mobile.common.R as CommonR

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BookReaderScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val epub = BookReaderUiState(bookType = BookType.EPUB, chapter = 6, chapterCount = 19)

    private fun show(state: BookReaderUiState, actions: BookReaderActions = BookReaderActions()) {
        composeRule.setContent {
            AppTheme(VaultBrand.BookVault) { BookReaderScreen(state, actions, pages = {}) }
        }
    }

    private fun string(resId: Int, vararg args: Any) =
        ApplicationProvider.getApplicationContext<Application>().getString(resId, *args)

    private fun openMenuItem(resId: Int) {
        composeRule.onNodeWithContentDescription(string(CommonR.string.vault_more_options)).performClick()
        composeRule.onNodeWithText(string(resId)).performClick()
    }

    // --- chapter bar --------------------------------------------------------------------------------

    @Test
    fun `the chapter bar shows the chapter's name and position`() {
        show(epub.copy(chapterTitle = "Introduction"))

        composeRule.onNodeWithText("Introduction").assertExists()
        composeRule.onNodeWithText("Chapter 7 of 19").assertExists()
        composeRule.onNodeWithContentDescription(string(R.string.chapter_prev)).assertIsEnabled()
        composeRule.onNodeWithContentDescription(string(R.string.chapter_next)).assertIsEnabled()
    }

    @Test
    fun `on the first chapter the back arrow is disabled`() {
        show(epub.copy(chapter = 0))

        composeRule.onNodeWithText("Chapter 1 of 19").assertExists()
        composeRule.onNodeWithContentDescription(string(R.string.chapter_prev)).assertIsNotEnabled()
    }

    @Test
    fun `on the last chapter the forward arrow is disabled`() {
        show(epub.copy(chapter = 18))

        composeRule.onNodeWithContentDescription(string(R.string.chapter_next)).assertIsNotEnabled()
    }

    @Test
    fun `the arrows go to the chapter either side`() {
        val went = mutableListOf<Int>()
        show(epub, BookReaderActions(onGoTo = { went += it }))

        composeRule.onNodeWithContentDescription(string(R.string.chapter_next)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.chapter_prev)).performClick()

        went shouldBe listOf(7, 5)
    }

    @Test
    fun `the chapter bar is only for an EPUB read as a scroll`() {
        show(epub.copy(scrollMode = false))

        composeRule.onNodeWithContentDescription(string(R.string.chapter_next)).assertDoesNotExist()
    }

    @Test
    fun `a PDF has no chapter bar`() {
        show(BookReaderUiState(bookType = BookType.PDF, chapter = 0, chapterCount = 180))

        composeRule.onNodeWithContentDescription(string(R.string.chapter_next)).assertDoesNotExist()
    }

    // --- top bar ------------------------------------------------------------------------------------

    @Test
    fun `while the book loads, it shows progress`() {
        show(epub.copy(loading = true))

        composeRule.onNodeWithTag(VaultTestTags.LOADING).assertExists()
    }

    @Test
    fun `once the book has loaded, the progress goes`() {
        show(epub)

        composeRule.onNodeWithTag(VaultTestTags.LOADING).assertDoesNotExist()
    }

    @Test
    fun `the mode button says what it switches to, and switches`() {
        var toggles = 0
        show(epub, BookReaderActions(onToggleScrollMode = { toggles++ }))

        composeRule.onNodeWithContentDescription(string(R.string.reader_page_mode)).performClick()

        toggles shouldBe 1
    }

    @Test
    fun `read page by page, the mode button offers the scroll`() {
        show(epub.copy(scrollMode = false))

        composeRule.onNodeWithContentDescription(string(R.string.reader_scroll_mode)).assertExists()
    }

    @Test
    fun `the night mode button says what it does, and does it`() {
        var toggles = 0
        show(epub, BookReaderActions(onToggleNightMode = { toggles++ }))

        composeRule.onNodeWithContentDescription(string(R.string.reader_night_mode_on)).performClick()

        toggles shouldBe 1
    }

    @Test
    fun `in night mode, the button turns it off`() {
        show(epub.copy(nightMode = true))

        composeRule.onNodeWithContentDescription(string(R.string.reader_night_mode_off)).assertExists()
    }

    @Test
    fun `back leaves the book`() {
        var backs = 0
        show(epub, BookReaderActions(onBack = { backs++ }))

        composeRule.onNodeWithContentDescription(string(CommonR.string.vault_back)).performClick()

        backs shouldBe 1
    }

    // --- contents -----------------------------------------------------------------------------------

    @Test
    fun `an EPUB's contents mark the chapter being read, and choosing one goes to it`() {
        var went: Int? = null
        val toc = listOf(EpubTocEntry("Prologue", 0), EpubTocEntry("Chapter One", 1), EpubTocEntry("Chapter Two", 3))
        show(epub.copy(chapter = 4, tableOfContents = toc), BookReaderActions(onGoTo = { went = it }))

        openMenuItem(R.string.contents_title)

        composeRule.onNodeWithText("Chapter Two").assertIsSelected()
        composeRule.onNodeWithText("Prologue").assertIsNotSelected()
        composeRule.onNodeWithText("Chapter One").performClick()
        went shouldBe 1
    }

    @Test
    fun `an EPUB with no contents says so`() {
        show(epub)

        openMenuItem(R.string.contents_title)

        composeRule.onNodeWithText(string(R.string.contents_empty)).assertExists()
    }

    @Test
    fun `a PDF's contents ask for a page and go to it`() {
        var went: Int? = null
        show(BookReaderUiState(bookType = BookType.PDF, chapterCount = 180), BookReaderActions(onGoTo = { went = it }))

        openMenuItem(R.string.contents_title)
        composeRule.onNodeWithText(string(R.string.go_to_page_hint)).performTextInput("42")
        composeRule.onNodeWithText(string(R.string.go)).performClick()

        went shouldBe 41
    }

    @Test
    fun `a page past the end of a PDF goes to its last page`() {
        var went: Int? = null
        show(BookReaderUiState(bookType = BookType.PDF, chapterCount = 180), BookReaderActions(onGoTo = { went = it }))

        openMenuItem(R.string.contents_title)
        composeRule.onNodeWithText(string(R.string.go_to_page_hint)).performTextInput("999")
        composeRule.onNodeWithText(string(R.string.go)).performClick()

        went shouldBe 179
    }

    // --- text settings ------------------------------------------------------------------------------

    @Test
    fun `text settings show each setting's value, and choosing a font size reports it`() {
        var chosen: Float? = null
        show(epub, BookReaderActions(onFontScale = { chosen = it }))

        openMenuItem(R.string.text_settings_title)
        composeRule.onNodeWithText("100%").assertExists()
        composeRule.onNodeWithText("Serif").assertExists()
        composeRule.onNodeWithText("1.0x").assertExists()
        composeRule.onNodeWithText(string(R.string.text_settings_font_size)).performClick()
        composeRule.onNodeWithText("125%").performClick()

        chosen shouldBe 1.25f
    }

    // --- bookmarks ----------------------------------------------------------------------------------

    @Test
    fun `adding a bookmark hands over the note`() {
        var note: String? = null
        show(epub, BookReaderActions(onAddBookmark = { note = it }))

        openMenuItem(R.string.add_bookmark_action)
        composeRule.onNodeWithText(string(R.string.add_bookmark_note_hint)).performTextInput("Great line here")
        composeRule.onNodeWithText(string(R.string.bookmark_save)).performClick()

        note shouldBe "Great line here"
    }

    @Test
    fun `with no bookmarks, the list says so`() {
        show(epub)

        openMenuItem(R.string.bookmarks_title)

        composeRule.onNodeWithText(string(R.string.no_bookmarks_yet)).assertExists()
    }

    @Test
    fun `tapping a bookmark goes to it`() {
        var opened: BookBookmarkEntity? = null
        show(epub.copy(bookmarks = listOf(bookmark)), BookReaderActions(onOpenBookmark = { opened = it }))

        openMenuItem(R.string.bookmarks_title)
        composeRule.onNodeWithText("Chapter 3").performClick()

        opened shouldBe bookmark
    }

    @Test
    fun `a bookmark can be deleted from the list`() {
        var deleted: Long? = null
        show(epub.copy(bookmarks = listOf(bookmark)), BookReaderActions(onDeleteBookmark = { deleted = it }))

        openMenuItem(R.string.bookmarks_title)
        composeRule.onNodeWithContentDescription(string(R.string.delete_bookmark, "Chapter 3")).performClick()

        deleted shouldBe 1L
    }

    @Test
    fun `a bookmark shows its note`() {
        show(epub.copy(bookmarks = listOf(bookmark.copy(note = "Great line"))))

        openMenuItem(R.string.bookmarks_title)

        composeRule.onNodeWithText("Great line", substring = true).assertExists()
    }

    private val bookmark = BookBookmarkEntity(
        id = 1L,
        bookItemId = "book-1",
        pageIndex = 2,
        label = "Chapter 3",
        note = null,
        createdAt = 0L,
    )
}
