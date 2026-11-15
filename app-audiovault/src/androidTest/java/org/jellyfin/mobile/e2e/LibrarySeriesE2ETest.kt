package org.jellyfin.mobile.e2e

import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.jellyfin.mobile.R
import org.jellyfin.mobile.testing.VaultFixtures.Audiobooks
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.Test

/**
 * The book in six parts on the test server shown as separate books, then grouped by series: the
 * series opens to its books in order. What's shown how stays on the device, cleared between tests.
 */
@BrowseArea
class LibrarySeriesE2ETest : LoggedInE2ETest() {

    @Test
    fun aBookShownAsSeparateBooksIsASeries() {
        loginAndWaitForLibrary()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { Audiobooks.MULTI_PART in libraryTitles() }

        composeRule.onNodeWithText(Audiobooks.MULTI_PART).performTouchInput { longClick() }
        composeRule.onNodeWithText(context.getString(R.string.show_as_separate_books)).performClick()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { "Part 1" in libraryTitles() }

        chooseFromLibraryMenu(context.getString(R.string.library_group_by))
        composeRule.onNodeWithText(context.getString(R.string.library_group_series)).performClick()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { Audiobooks.MULTI_PART in libraryTitles() }

        composeRule.onNodeWithText(Audiobooks.MULTI_PART).performClick()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { libraryTitles().take(2) == listOf("Part 1", "Part 2") }

        // Grouping is a setting, kept past this test: put it back
        chooseFromLibraryMenu(context.getString(R.string.library_group_by))
        composeRule.onNodeWithText(context.getString(R.string.library_group_none)).performClick()
    }
}
