package org.jellyfin.mobile.e2e

import android.view.View
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.downloads.ItemDownloads
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.data.JellyfinDatabase
import org.jellyfin.mobile.testing.endtoend.SignedInE2ETest
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.jellyfin.mobile.common.R as CommonR

/**
 * Base for any e2e test that needs to start from a logged-in library screen — the connect/login
 * dance itself is exercised directly by [LoginFlowE2ETest]; this just gets other flows past it.
 */
abstract class LoggedInE2ETest : SignedInE2ETest<MainActivity>(MainActivity::class.java) {

    override fun clearAppState() {
        koinGet<JellyfinDatabase>().clearAllTables()
        // Downloaded files outlive the database tables; without this a later test would find a
        // track already downloaded by an earlier one.
        onMain { koinGet<ItemDownloads>().run { downloads.value.keys.forEach(::remove) } }
    }

    /** Connects and logs in, returning once the library shows at least one item. */
    protected fun loginAndWaitForLibrary() {
        connectAndSignIn()
        waitForLibraryItems()
    }

    /** Waits until the library shows at least one item. */
    protected fun waitForLibraryItems(timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis) { libraryItems().fetchSemanticsNodes().isNotEmpty() }
    }

    /** The items the library has on screen (a lazy list only composes those). */
    protected fun libraryItems(): SemanticsNodeInteractionCollection =
        composeRule.onAllNodesWithTag(VaultTestTags.LIBRARY_ITEM)

    /** The titles of the items on screen, top to bottom. */
    protected fun libraryTitles(): List<String> = libraryItems().fetchSemanticsNodes().mapNotNull { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
    }

    /** Opens the library's "More options" menu and chooses [label]. */
    protected fun chooseFromLibraryMenu(label: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.vault_more_options)).performClick()
        composeRule.onNodeWithText(label).performClick()
    }

    /**
     * Taps the first library item (always playable, never a browse-only folder, in the default
     * flattened-library mode) and waits for playback to start. Call [loginAndWaitForLibrary] first.
     */
    protected fun startPlaybackFromFirstLibraryItem() {
        waitForLibraryItems()
        libraryItems()[0].performClick()

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
