package org.jellyfin.mobile.books.e2e

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.jellyfin.mobile.books.BookMainActivity
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.data.BookDatabase
import org.jellyfin.mobile.testing.endtoend.SignedInE2ETest
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS

/**
 * Base for any e2e test that needs to start from a logged-in library screen — the connect/login
 * dance itself is exercised directly by [LoginFlowE2ETest]; this just gets other flows past it.
 */
abstract class LoggedInE2ETest : SignedInE2ETest<BookMainActivity>(BookMainActivity::class.java) {

    override fun clearAppState() {
        koinGet<BookDatabase>().clearAllTables()
    }

    /** Connects and logs in, returning once the library's item list is on screen. */
    protected fun loginAndWaitForLibrary() {
        connectAndSignIn()
        waitForView(allOf(withId(R.id.recycler_view_list), hasMinimumChildCount(1)))
    }

    /**
     * Taps the library item at [position] (default: the first) and waits for the reader screen to
     * finish loading it (the progress bar disappearing is the reader's own loading signal — see
     * `BookReaderFragment`'s `loading` collector).
     */
    protected fun openBookAtPosition(position: Int = 0) {
        onView(withId(R.id.recycler_view_list))
            .perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(position, click()))

        waitForReaderToFinishLoading()
    }

    /** Waits for `BookReaderFragment`'s progress bar to hide, meaning a page/chapter has rendered. */
    protected fun waitForReaderToFinishLoading(timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            runCatching { onView(withId(R.id.progress_bar)).check(matches(not(isDisplayed()))) }.isSuccess
        }
    }
}
