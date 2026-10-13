package org.jellyfin.mobile.e2e

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.allOf
import org.jellyfin.mobile.R
import org.junit.Test

/**
 * Hides the first library item via its long-press menu (`hide_action`, already exercised at the
 * unit level in `LibraryFragmentTest`), confirms it drops out of the list, then confirms toggling
 * "Show hidden items" (`R.id.action_show_hidden`) brings it back. Hidden-item state lives in
 * `HiddenItemEntity`/Room, which [BaseE2ETest.resetAppState] already clears before/after every
 * test — no manual unhide needed for cleanup.
 */
class LibraryHideItemE2ETest : LoggedInE2ETest() {

    @Test
    fun hidingAnItemRemovesItUntilShowHiddenIsEnabled() {
        loginAndWaitForLibrary()
        waitForView(allOf(withId(R.id.recycler_view), hasMinimumChildCount(1)))
        val originalCount = itemCount()

        onView(withId(R.id.recycler_view))
            .perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(0, longClick()))

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        onView(withText(context.getString(R.string.hide_action))).inRoot(isDialog()).perform(click())

        waitForItemCount(originalCount - 1)

        openActionBarOverflowOrOptionsMenu(context)
        onView(withText(context.getString(R.string.show_hidden_items))).perform(click())

        waitForItemCount(originalCount)
    }

    private fun itemCount(): Int {
        var count = -1
        onView(withId(R.id.recycler_view)).check { view, _ ->
            count = (view as RecyclerView).adapter?.itemCount ?: -1
        }
        return count
    }

    private fun waitForItemCount(expected: Int) {
        composeRule.waitUntil(timeoutMillis = 15_000L) { itemCount() == expected }
    }
}
