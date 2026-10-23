package org.jellyfin.mobile.e2e

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import org.jellyfin.mobile.R
import org.junit.Test

/**
 * Library toolbar overflow -> Settings -> Sign out, confirmed -> lands back on the sign-in screen
 * (the server itself stays configured, only `currentUserId` is cleared — see
 * `MainViewModel.logout()` / `MainActivity.handleAvailableServerState`).
 */
@AccountArea
class LogoutE2ETest : LoggedInE2ETest() {

    @Test
    fun signingOutReturnsToLogin() {
        loginAndWaitForLibrary()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        chooseFromLibraryMenu(context.getString(R.string.activity_name_settings))

        val signOut = context.getString(R.string.pref_sign_out)
        waitForComposeText(signOut)
        composeRule.onNodeWithText(signOut).performScrollTo().performClick()
        composeRule.onNodeWithText(context.getString(R.string.sign_out_yes)).performScrollTo().performClick()

        waitForSignInScreen()
    }
}
