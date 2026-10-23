package org.jellyfin.mobile.books.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.junit.Test

/**
 * Connect (Compose `ConnectScreen`) -> Login (common's Compose `LoginScreen`, shared with
 * `app-audiovault` via the `common` module) -> lands on the library (`BookLibraryRoute`). Runs against the
 * test server (see [BaseE2ETest]).
 */
@AccountArea
class LoginFlowE2ETest : LoggedInE2ETest() {

    @Smoke
    @Test
    fun loginThroughToLibrary() {
        loginAndWaitForLibrary()

        composeRule.onNodeWithTag(VaultTestTags.LIBRARY_LIST).assertIsDisplayed()
    }
}
