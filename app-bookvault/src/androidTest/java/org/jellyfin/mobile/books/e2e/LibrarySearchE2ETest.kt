package org.jellyfin.mobile.books.e2e

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.Matchers.allOf
import org.jellyfin.mobile.books.R
import org.junit.Test

/**
 * `BookLibraryViewModel`'s search filters the already-loaded list client-side by name/author/
 * series (see `filterAndSortBooks`/`BookLibraryFilter` in the unit suite) — no extra network round
 * trip. Searches for a title known to exist on the real Vault library ("Palestine") rather than a
 * generic term, so the assertion that the list actually narrowed isn't just luck.
 */
class LibrarySearchE2ETest : LoggedInE2ETest() {

    @Test
    fun searchingFiltersTheLibraryList() {
        loginAndWaitForLibrary()
        waitForView(allOf(withId(R.id.recycler_view_list), hasMinimumChildCount(1)))
        val fullCount = itemCount()

        onView(withId(R.id.action_search)).perform(click())
        onView(withId(androidx.appcompat.R.id.search_src_text)).perform(typeText("Palestine"))

        waitForView(allOf(withId(R.id.recycler_view_list), hasMinimumChildCount(1)))
        val filteredCount = itemCount()

        assert(filteredCount in 1 until fullCount) {
            "Expected the search to narrow the list (full=$fullCount, filtered=$filteredCount)"
        }
    }

    private fun itemCount(): Int {
        var count = -1
        onView(withId(R.id.recycler_view_list)).check { view, _ ->
            count = (view as RecyclerView).adapter?.itemCount ?: -1
        }
        return count
    }
}
