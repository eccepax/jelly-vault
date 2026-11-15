package org.jellyfin.mobile.e2e

import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.jellyfin.mobile.R
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.Test

/**
 * Marks the first book finished from its long-press menu, against the test server, and finds it
 * under "Finished only"; then marks it not finished again, which also puts the server back as it was.
 */
@BrowseArea
class LibraryFinishedE2ETest : LoggedInE2ETest() {

    @Test
    fun aBookMarkedFinishedShowsUnderFinishedOnly() {
        loginAndWaitForLibrary()
        val title = libraryTitles().first()

        mark(title, R.string.mark_finished_action)
        chooseFromLibraryMenu(context.getString(R.string.filter_finished_only))
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { libraryTitles() == listOf(title) }

        mark(title, R.string.mark_unfinished_action)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { title !in libraryTitles() }
    }

    private fun mark(title: String, action: Int) {
        composeRule.onNodeWithText(title).performTouchInput { longClick() }
        composeRule.onNodeWithText(context.getString(action)).performClick()
    }
}
