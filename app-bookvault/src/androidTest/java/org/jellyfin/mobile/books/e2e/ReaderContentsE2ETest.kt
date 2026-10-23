package org.jellyfin.mobile.books.e2e

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.ui.test.assertIsSelected
import androidx.core.view.children
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.testing.VaultFixtures.Books
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.Test

/** Going straight to part of a book: an EPUB's table of contents, a PDF's page number. */
@ReadingArea
class ReaderContentsE2ETest : LoggedInE2ETest() {

    @Test
    fun choosingAnEntryInTheContentsGoesToItsChapter() {
        loginAndWaitForLibrary()
        openBook(Books.EPUB)
        waitForChapter(0)

        readerMenu(R.string.contents_title)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { entries().fetchSemanticsNodes().size > ENTRY }
        entries()[ENTRY].performClick()

        // The book moved on, opened partway into the file the chapter shares with others (in this
        // book, the entry isn't its file's first), and the contents now mark it as the one being read
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeRule.onAllNodesWithText(chapterShown(0), substring = true).fetchSemanticsNodes().isEmpty()
        }
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { pageScrolledDown() }
        readerMenu(R.string.contents_title)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { entries().fetchSemanticsNodes().size > ENTRY }
        entries()[ENTRY].assertIsSelected()
    }

    @Test
    fun goingToAPageNumberInAPdfGoesToThatPage() {
        loginAndWaitForLibrary()
        openBook(Books.FIRST_BY_TITLE_PDF)

        readerMenu(R.string.contents_title)
        composeRule.onNodeWithText(context.getString(R.string.go_to_page_hint))
            .performTextInput(Books.FIRST_BY_TITLE_PDF_PAGES.toString())
        composeRule.onNodeWithText(context.getString(R.string.go)).performClick()

        waitForSavedPlace(Books.FIRST_BY_TITLE_PDF_PAGES - 1)
    }

    /** Whether the chapter on screen opened scrolled down its page, at its entry, rather than at the top of the file. */
    private fun pageScrolledDown(): Boolean {
        var scrolled = false
        composeRule.activityRule.scenario.onActivity { activity ->
            scrolled = activity.window.decorView.webViews().any { it.isShown && it.scrollY > 0 }
        }
        return scrolled
    }

    private fun View.webViews(): List<WebView> = when (this) {
        is WebView -> listOf(this)
        is ViewGroup -> children.flatMap { it.webViews() }.toList()
        else -> emptyList()
    }

    /** The contents' entries, each a choice. */
    private fun entries() = composeRule.onAllNodes(isSelectable())

    /** "Chapter 1 of ", as the chapter bar starts for chapter [chapter] (from 0). */
    private fun chapterShown(chapter: Int) =
        context.getString(R.string.chapter_position, chapter + 1, 0).removeSuffix("0")

    private companion object {
        /** An entry well into the book, past any front matter that shares the first chapter. */
        const val ENTRY = 5
    }
}
