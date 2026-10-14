package org.jellyfin.mobile.e2e

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.allOf
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.R as CommonR
import org.junit.Test
import org.jellyfin.mobile.common.R as CommonR

/**
 * Library toolbar overflow -> Settings (`ModernAndroidPreferences`-based, a RecyclerView of rows
 * matched by text rather than stable ids) -> Sign out -> lands back on `LoginFragment` (the server
 * itself stays configured, only `currentUserId` is cleared — see `MainViewModel.logout()` /
 * `MainActivity.handleAvailableServerState`).
 *
 * `LibraryFragment` -> `SettingsFragment` navigation uses a plain `FragmentTransaction.add()`
 * (`ActivityEventHandler.kt`'s `OpenSettings` handling / the `addFragment` extension) rather than
 * `replace()`, so the library screen's view — including its own `R.id.recycler_view` — stays fully
 * attached underneath, just visually covered. Both recycler views report VISIBLE (sibling z-order
 * occlusion isn't visible to Espresso's matcher), so `withId(R.id.recycler_view)` alone is
 * ambiguous here — scoped via `hasSibling` to the settings screen's own toolbar title instead.
 */
class LogoutE2ETest : LoggedInE2ETest() {

    @Test
    fun signingOutReturnsToLogin() {
        loginAndWaitForLibrary()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        openActionBarOverflowOrOptionsMenu(context)
        onView(withText(context.getString(R.string.activity_name_settings))).perform(click())

        val settingsRecyclerView = allOf(
            withId(R.id.recycler_view),
            hasSibling(hasDescendant(withText(context.getString(R.string.activity_name_settings)))),
        )
        onView(settingsRecyclerView).perform(
            scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(context.getString(R.string.pref_sign_out)))),
        )
        onView(withText(context.getString(R.string.pref_sign_out))).perform(click())

        waitForView(CommonR.id.editUsername)
        onView(withId(CommonR.id.editUsername)).check(matches(isDisplayed()))
    }
}
