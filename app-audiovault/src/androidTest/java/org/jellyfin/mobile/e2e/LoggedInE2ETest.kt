package org.jellyfin.mobile.e2e

import android.content.Context
import android.view.View
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.recyclerview.widget.RecyclerView
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
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.R as CommonR
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule

private const val WAIT_TIMEOUT_MS = 15_000L

/**
 * Base for any e2e test that needs to start from a logged-in library screen — the connect/login
 * dance itself is exercised directly by [LoginFlowE2ETest]; this just gets other flows past it.
 */
abstract class LoggedInE2ETest : BaseE2ETest() {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

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

        composeRule.onNodeWithText(hostHint).performTextInput(e2eHostname())
        composeRule.onNodeWithText(connectButtonText).performClick()

        waitForView(CommonR.id.editUsername)
        onView(withId(CommonR.id.editUsername)).perform(replaceText(e2eUsername()))
        onView(withId(CommonR.id.editPassword)).perform(replaceText(e2ePassword()))
        onView(withId(CommonR.id.buttonLogin)).perform(click())

        waitForView(R.id.recycler_view)
    }

    /**
     * Taps the first library item (always playable, never a browse-only folder, in the default
     * flattened-library mode) and waits for playback to start. Call [loginAndWaitForLibrary] first.
     */
    protected fun startPlaybackFromFirstLibraryItem() {
        // The recycler view is already displayed at this point, but the library fetch is async —
        // wait for it to actually be populated before tapping a position that may not exist yet.
        waitForView(allOf(withId(R.id.recycler_view), hasMinimumChildCount(1)))

        onView(withId(R.id.recycler_view))
            .perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(0, click()))

        waitForView(playerControl(R.id.exo_play_pause))
    }

    /**
     * Matcher for a button inside the player's custom ExoPlayer control layout
     * (`exo_player_control_view.xml`) — `PlayerView` re-inflates that layout internally, leaving a
     * second (GONE) copy with the same id as the real, visible one (see the comment on
     * `PlayerFragment.onCreateView` and `PlayerFragmentTest.controlButton`); [isDisplayed] is what
     * disambiguates which one is real.
     */
    protected fun playerControl(id: Int): Matcher<View> = allOf(withId(id), isDisplayed())

    /**
     * ExoPlayer's `PlayerView` controller auto-hides its whole control overlay (including
     * `exo_position`, the chapter buttons, etc — not just `exo_play_pause`) after a few seconds of
     * no interaction. Tap the player surface to wake it back up before waiting for a specific
     * control, rather than assuming it's still showing just because it was a moment ago.
     */
    protected fun waitForPlayerControl(id: Int, timeoutMillis: Long = WAIT_TIMEOUT_MS): Matcher<View> {
        val matcher = playerControl(id)
        if (runCatching { onView(matcher).check(matches(isDisplayed())) }.isFailure) {
            runCatching { onView(withId(R.id.player_view)).perform(click()) }
        }
        waitForView(matcher, timeoutMillis)
        return matcher
    }

    protected fun waitForView(viewId: Int, timeoutMillis: Long = WAIT_TIMEOUT_MS) =
        waitForView(withId(viewId), timeoutMillis)

    /**
     * Matcher-based overload: needed wherever a plain `withId` would be ambiguous — e.g. ExoPlayer's
     * `PlayerView` re-inflates its control layout internally, leaving a second (GONE) view with the
     * same id as the real, visible one (see the comment on `PlayerFragment.onCreateView` and
     * `PlayerFragmentTest.controlButton`) — combine with [isDisplayed] there to disambiguate.
     */
    protected fun waitForView(matcher: Matcher<View>, timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            runCatching { onView(matcher).check(matches(isDisplayed())) }.isSuccess
        }
    }
}
