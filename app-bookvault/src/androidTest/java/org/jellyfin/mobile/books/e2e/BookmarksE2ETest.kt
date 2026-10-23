package org.jellyfin.mobile.books.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.testing.VaultFixtures.Books
import org.junit.Test

/** A bookmark saved in one chapter is listed, goes back to that chapter, and can be deleted. */
@ReadingArea
class BookmarksE2ETest : LoggedInE2ETest() {

    @Test
    fun aBookmarkTakesYouBackToItsChapterAndCanBeDeleted() {
        loginAndWaitForLibrary()
        openBook(Books.EPUB)
        nextChapter()
        nextChapter()
        waitForChapter(BOOKMARKED)

        readerMenu(R.string.add_bookmark_action)
        composeRule.onNodeWithText(context.getString(R.string.bookmark_save)).performClick()
        nextChapter()
        waitForChapter(BOOKMARKED + 1)

        // Listed under its chapter's name, and opening it goes back there
        readerMenu(R.string.bookmarks_title)
        val label = bookmarkLabel()
        composeRule.onNode(hasText(label) and hasClickAction()).performClick()
        waitForChapter(BOOKMARKED)

        readerMenu(R.string.bookmarks_title)
        composeRule.onNodeWithContentDescription(context.getString(R.string.delete_bookmark, label)).performClick()
        waitForComposeText(context.getString(R.string.no_bookmarks_yet))
    }

    private fun nextChapter() {
        composeRule.onNodeWithContentDescription(context.getString(R.string.chapter_next)).performClick()
    }

    /** The one bookmark's label, read off its delete button ("Delete bookmark <label>"). */
    private fun bookmarkLabel(): String {
        val delete = context.getString(R.string.delete_bookmark, "")
        composeRule.waitUntil { composeRule.onAllNodes(deleteButton(delete)).fetchSemanticsNodes().isNotEmpty() }
        return composeRule.onAllNodes(deleteButton(delete)).fetchSemanticsNodes().single()
            .config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().single().removePrefix(delete)
    }

    private fun deleteButton(prefix: String) = hasContentDescription(prefix, substring = true)

    private companion object {
        /** The chapter bookmarked, from 0. */
        const val BOOKMARKED = 2
    }
}
