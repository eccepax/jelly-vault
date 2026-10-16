package org.jellyfin.mobile.books.e2e

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.common.R as CommonR
import org.junit.Test

/**
 * Library toolbar overflow -> Settings (`R.id.action_settings`, `showAsAction="never"` so it only
 * lives in the overflow menu) -> Settings screen (Compose) -> Sign out, confirmed -> lands back on
 * `LoginFragment` (the server itself stays configured, only `currentUserId` is cleared — see
 * `MainViewModel.logout()`).
 */
class LogoutE2ETest : LoggedInE2ETest() {

    @Test
    fun signingOutReturnsToLogin() {
        loginAndWaitForLibrary()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        openActionBarOverflowOrOptionsMenu(context)
        onView(withText(context.getString(R.string.settings))).perform(click())

        composeRule.onNodeWithText(context.getString(R.string.pref_sign_out)).performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.sign_out_yes)).performScrollTo().performClick()

        waitForView(CommonR.id.editUsername)
        onView(withId(CommonR.id.editUsername)).check(matches(isDisplayed()))
    }
}
