package org.jellyfin.mobile.books.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.testing.VaultFixtures.Books
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.Test

/**
 * `BookLibraryViewModel`'s search filters the already-loaded list client-side by name/author/
 * series (see `filterAndSortBooks`/`BookLibraryFilter` in the unit suite) — no extra network round
 * trip. Searches for a word from one fixture book's title rather than a
 * generic term, and checks the library showed other books first, so the list narrowing isn't luck.
 */
class LibrarySearchE2ETest : LoggedInE2ETest() {

    private val otherBooks = hasTestTag(VaultTestTags.LIBRARY_ITEM) and
        !hasText(QUERY, substring = true, ignoreCase = true)

    @Test
    fun searchingFiltersTheLibraryList() {
        loginAndWaitForLibrary()
        assert(composeRule.onAllNodes(otherBooks).fetchSemanticsNodes().isNotEmpty()) {
            "Expected the library to show books other than \"$QUERY\" before searching"
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onNodeWithContentDescription(context.getString(R.string.action_search)).performClick()
        composeRule.onNodeWithTag(VaultTestTags.SEARCH_FIELD).performTextInput(QUERY)

        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            composeRule.onAllNodes(otherBooks).fetchSemanticsNodes().isEmpty() &&
                libraryItems().fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val QUERY = Books.SEARCH_TERM
    }
}
