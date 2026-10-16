package org.jellyfin.mobile.e2e

import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import org.jellyfin.mobile.R
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
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
        val title = libraryTitles().first()

        libraryItems()[0].performTouchInput { longClick() }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onNodeWithText(context.getString(R.string.hide_action)).performClick()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) { title !in libraryTitles() }

        chooseFromLibraryMenu(context.getString(R.string.show_hidden_items))

        composeRule.waitUntil(WAIT_TIMEOUT_MS) { title in libraryTitles() }
    }
}
