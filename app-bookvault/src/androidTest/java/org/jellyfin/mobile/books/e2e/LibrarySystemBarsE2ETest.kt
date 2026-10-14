package org.jellyfin.mobile.books.e2e

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.jellyfin.mobile.books.R
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

        onView(withId(R.id.recycler_view_list)).check { list, noViewFound ->
            if (noViewFound != null) throw noViewFound
            val navBar = ViewCompat.getRootWindowInsets(list)!!
                .getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val location = IntArray(2).also { list.getLocationInWindow(it) }
            val listBottom = location[1] + list.height
            val navBarTop = list.rootView.height - navBar
            check(listBottom <= navBarTop) {
                "The library list ends at y=$listBottom, under the navigation bar starting at y=$navBarTop"
            }
        }
    }
}
