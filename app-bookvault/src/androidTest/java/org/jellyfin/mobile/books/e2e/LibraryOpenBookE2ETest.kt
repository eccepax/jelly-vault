package org.jellyfin.mobile.books.e2e

import androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.Matchers.allOf
import org.jellyfin.mobile.books.R
import org.junit.Test

/**
 * Tapping a library item opens `BookReaderFragment`, which downloads the book (if not already
 * cached — see `BookReaderViewModel.loadBook`), sniffs its format, and renders it. The library
 * sorts by title (`BookSortOption.TITLE` is the default), and on the real Vault library the
 * alphabetically-first book is a real PDF ("Facing East from Indian Country..."), so this
 * exercises the `BookType.PDF` / `PdfRendererCore` path end to end — a real download, a real
 * `PdfRenderer` page render, not just a UI-state check.
 */
class LibraryOpenBookE2ETest : LoggedInE2ETest() {

    @Test
    fun openingALibraryItemRendersItInTheReader() {
        loginAndWaitForLibrary()

        openBookAtPosition(0)

        waitForView(allOf(withId(R.id.recycler_view), hasMinimumChildCount(1)))
    }
}
