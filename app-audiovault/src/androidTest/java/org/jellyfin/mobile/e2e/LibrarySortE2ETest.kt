package org.jellyfin.mobile.e2e

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.jellyfin.mobile.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Self-verifying rather than checking against specific known titles (real library content isn't
 * something to hardcode expectations against): after switching to "Sort by Title", whatever's
 * currently on screen must be in ascending alphabetical order.
 */
class LibrarySortE2ETest : LoggedInE2ETest() {

    @Test
    fun sortingByTitleOrdersVisibleItemsAlphabetically() {
        loginAndWaitForLibrary()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        chooseFromLibraryMenu(context.getString(R.string.library_sort_by))
        composeRule.onNodeWithText(context.getString(R.string.library_sort_title)).performClick()

        waitForLibraryItems()
        val titles = libraryTitles()

        assertEquals(titles.sortedWith(String.CASE_INSENSITIVE_ORDER), titles)
    }
}
