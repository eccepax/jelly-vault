package org.jellyfin.mobile.books.e2e

import androidx.compose.ui.test.onNodeWithTag
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.junit.Test

/**
 * The library list must end above the navigation bar on a real device. The Robolectric version of
 * this check can't run below API 30: AndroidX derives nav-bar insets there from the window's stable
 * insets, which Robolectric doesn't have.
 */
class LibrarySystemBarsE2ETest : LoggedInE2ETest() {

    @Test
    fun theLibraryListEndsAboveTheNavigationBar() {
        loginAndWaitForLibrary()

        val listBottom = composeRule.onNodeWithTag(VaultTestTags.LIBRARY_LIST).fetchSemanticsNode().boundsInWindow.bottom
        val navBarTop = onMain {
            val window = composeRule.activity.window.decorView
            val navBar = ViewCompat.getRootWindowInsets(window)!!.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            window.height - navBar
        }
        check(listBottom <= navBarTop) {
            "The library list ends at y=$listBottom, under the navigation bar starting at y=$navBarTop"
        }
    }
}
