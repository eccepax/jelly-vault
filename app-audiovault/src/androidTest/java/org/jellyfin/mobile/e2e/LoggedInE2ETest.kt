package org.jellyfin.mobile.e2e

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.JellyfinDatabase
import org.jellyfin.mobile.testing.endtoend.SignedInE2ETest
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS

/**
 * Base for any e2e test that needs to start from a logged-in library screen — the connect/login
 * dance itself is exercised directly by [LoginFlowE2ETest]; this just gets other flows past it.
 */
abstract class LoggedInE2ETest : SignedInE2ETest<MainActivity>(MainActivity::class.java) {

    override fun clearAppState() {
        koinGet<JellyfinDatabase>().clearAllTables()
    }

    /** Connects and logs in, returning once the library's item list is on screen. */
    protected fun loginAndWaitForLibrary() {
        connectAndSignIn()
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
}
