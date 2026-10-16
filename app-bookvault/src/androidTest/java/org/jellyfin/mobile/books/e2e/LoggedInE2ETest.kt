package org.jellyfin.mobile.books.e2e

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.Matchers.not
import org.jellyfin.mobile.books.BookMainActivity
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.data.BookDatabase
import org.jellyfin.mobile.common.downloads.ItemDownloads
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.testing.endtoend.SignedInE2ETest
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS

/**
 * Base for any e2e test that needs to start from a logged-in library screen — the connect/login
 * dance itself is exercised directly by [LoginFlowE2ETest]; this just gets other flows past it.
 */
abstract class LoggedInE2ETest : SignedInE2ETest<BookMainActivity>(BookMainActivity::class.java) {

    override fun clearAppState() {
        koinGet<BookDatabase>().clearAllTables()
        // Downloaded files outlive the database tables; without this a later test would find a
        // book already downloaded by an earlier one.
        onMain { koinGet<ItemDownloads>().run { downloads.value.keys.forEach(::remove) } }
    }

    /** Connects and logs in, returning once the library shows at least one book. */
    protected fun loginAndWaitForLibrary() {
        connectAndSignIn()
        waitForLibraryItems()
    }

    /** Waits until the library shows at least one book. */
    protected fun waitForLibraryItems(timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) { libraryItems().fetchSemanticsNodes().isNotEmpty() }
    }

    /** The books the library has on screen (a lazy list only composes those). */
    protected fun libraryItems() = composeRule.onAllNodesWithTag(VaultTestTags.LIBRARY_ITEM)

    /**
     * Taps the library item at [position] (default: the first) and waits for the reader screen to
     * finish loading it (the progress bar disappearing is the reader's own loading signal — see
     * `BookReaderFragment`'s `loading` collector).
     */
    protected fun openBookAtPosition(position: Int = 0) {
        libraryItems()[position].performClick()

        waitForReaderToFinishLoading()
    }

    /** Waits for `BookReaderFragment`'s progress bar to hide, meaning a page/chapter has rendered. */
    protected fun waitForReaderToFinishLoading(timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            runCatching { onView(withId(R.id.progress_bar)).check(matches(not(isDisplayed()))) }.isSuccess
        }
    }
}
