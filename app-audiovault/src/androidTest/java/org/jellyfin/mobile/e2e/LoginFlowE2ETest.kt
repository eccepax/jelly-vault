package org.jellyfin.mobile.e2e

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.jellyfin.mobile.R
import org.junit.Test

/**
 * Connect (Compose `ConnectScreen`) -> Login (classic-View `LoginFragment`) -> lands on
 * `LibraryFragment`. Runs against the real local Vault server (see [BaseE2ETest]).
 */
class LoginFlowE2ETest : LoggedInE2ETest() {

    @Test
    fun loginThroughToLibrary() {
        loginAndWaitForLibrary()

        onView(withId(R.id.recycler_view)).check(matches(isDisplayed()))
    }
}
