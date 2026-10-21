package org.jellyfin.mobile.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.junit.Test

/**
 * Connect (Compose `ConnectScreen`) -> Login (common's Compose `LoginScreen`) -> lands on
 * `LibraryFragment`. Runs against the test server (see [BaseE2ETest]).
 */
@Smoke
@AccountArea
class LoginFlowE2ETest : LoggedInE2ETest() {

    @Test
    fun loginThroughToLibrary() {
        loginAndWaitForLibrary()

        composeRule.onNodeWithTag(VaultTestTags.LIBRARY_LIST).assertIsDisplayed()
    }
}
