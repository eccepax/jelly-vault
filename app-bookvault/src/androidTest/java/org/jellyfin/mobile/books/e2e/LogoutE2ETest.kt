package org.jellyfin.mobile.books.e2e

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.common.R as CommonR
import org.junit.Test

/**
 * Library toolbar overflow -> Settings (`R.id.action_settings`, `showAsAction="never"` so it only
 * lives in the overflow menu) -> Settings screen (`ModernAndroidPreferences`-based, a RecyclerView
 * of rows matched by text rather than stable ids) -> Sign out -> lands back on `LoginFragment` (the
 * server itself stays configured, only `currentUserId` is cleared — see `MainViewModel.logout()`).
 *
 * Unlike `app-audiovault`'s equivalent test, `BookLibraryFragment.openSettings()` uses
 * `FragmentTransaction.replace()` (not `add()`), so the library screen's view is fully torn down
 * once Settings is showing — no sibling-recyclerview ambiguity to work around here.
 */
class LogoutE2ETest : LoggedInE2ETest() {

    @Test
    fun signingOutReturnsToLogin() {
        loginAndWaitForLibrary()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        openActionBarOverflowOrOptionsMenu(context)
        onView(withText(context.getString(R.string.settings))).perform(click())

        onView(withId(R.id.recycler_view)).perform(
            scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(context.getString(R.string.pref_sign_out)))),
        )
        onView(withText(context.getString(R.string.pref_sign_out))).perform(click())

        waitForView(CommonR.id.editUsername)
        onView(withId(CommonR.id.editUsername)).check(matches(isDisplayed()))
    }
}
