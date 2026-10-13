package org.jellyfin.mobile.e2e

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.Matchers.allOf
import org.jellyfin.mobile.R
import org.junit.Test

/**
 * `LibraryViewModel.setFilterQuery` filters the already-loaded list client-side (see
 * `LibraryItemFilterTest` in the unit suite) — no extra network round trip once the library has
 * loaded. Searches for a title known to exist on the real Vault library ("Caste", Isabel
 * Wilkerson's "Caste: The Origins of Our Discontents") rather than a generic term, so the
 * assertion that the list actually narrowed isn't just luck.
 */
class LibrarySearchE2ETest : LoggedInE2ETest() {

    @Test
    fun searchingFiltersTheLibraryList() {
        loginAndWaitForLibrary()
        waitForView(allOf(withId(R.id.recycler_view), hasMinimumChildCount(1)))
        val fullCount = itemCount()

        onView(withId(R.id.action_search)).perform(click())
        onView(withId(androidx.appcompat.R.id.search_src_text)).perform(typeText("Caste"))

        waitForView(allOf(withId(R.id.recycler_view), hasMinimumChildCount(1)))
        val filteredCount = itemCount()

        assert(filteredCount in 1 until fullCount) {
            "Expected the search to narrow the list (full=$fullCount, filtered=$filteredCount)"
        }
    }

    private fun itemCount(): Int {
        var count = -1
        onView(withId(R.id.recycler_view)).check { view, _ ->
            count = (view as RecyclerView).adapter?.itemCount ?: -1
        }
        return count
    }
}
