package org.jellyfin.mobile.books.e2e

import android.content.Context
import android.view.View
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.jellyfin.mobile.books.BookMainActivity
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.common.R as CommonR
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule

private const val WAIT_TIMEOUT_MS = 15_000L

/**
 * Base for any e2e test that needs to start from a logged-in library screen — the connect/login
 * dance itself is exercised directly by [LoginFlowE2ETest]; this just gets other flows past it.
 * Mirrors `app-audiovault`'s `LoggedInE2ETest` — same shared `common` connect/login screens, same
 * real-server approach.
 */
abstract class LoggedInE2ETest : BaseE2ETest() {

    @get:Rule
    val composeRule = createAndroidComposeRule<BookMainActivity>()

    @Before
    fun requireCredentials() {
        assumeFalse("e2e.username not configured in local.properties — skipping", e2eUsername().isBlank())
        assumeFalse("e2e.password not configured in local.properties — skipping", e2ePassword().isBlank())
    }

    /** Connects and logs in, returning once the library's item list is on screen. */
    protected fun loginAndWaitForLibrary() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val hostHint = context.getString(CommonR.string.host_input_hint)
        val connectButtonText = context.getString(CommonR.string.connect_button_text)

        // BookMainActivity only calls setContent{} once MainViewModel.serverState first emits
        // (behind lifecycle.withStarted), which races the test rule's very first interaction on a
        // cold-started APK — retry until the Compose hierarchy actually exists.
        waitForComposeText(hostHint)
        composeRule.onNodeWithText(hostHint).performTextInput(e2eHostname())
        composeRule.onNodeWithText(connectButtonText).performClick()

        waitForView(CommonR.id.editUsername)
        onView(withId(CommonR.id.editUsername)).perform(replaceText(e2eUsername()))
        onView(withId(CommonR.id.editPassword)).perform(replaceText(e2ePassword()))
        onView(withId(CommonR.id.buttonLogin)).perform(click())

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

    private fun waitForComposeText(text: String, timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            runCatching { composeRule.onNodeWithText(text).assertExists() }.isSuccess
        }
    }

    protected fun waitForView(viewId: Int, timeoutMillis: Long = WAIT_TIMEOUT_MS) =
        waitForView(withId(viewId), timeoutMillis)

    protected fun waitForView(matcher: Matcher<View>, timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            runCatching { onView(matcher).check(matches(isDisplayed())) }.isSuccess
        }
    }
}
