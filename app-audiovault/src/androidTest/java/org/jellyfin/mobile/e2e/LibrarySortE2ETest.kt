package org.jellyfin.mobile.e2e

import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.allOf
import org.jellyfin.mobile.R
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Self-verifying rather than checking against specific known titles (real library content isn't
 * something to hardcode expectations against): after switching to "Sort by Title", whatever's
 * currently rendered in the RecyclerView's visible window must be in ascending alphabetical order.
 */
class LibrarySortE2ETest : LoggedInE2ETest() {

    @Test
    fun sortingByTitleOrdersVisibleItemsAlphabetically() {
        loginAndWaitForLibrary()
        waitForView(allOf(withId(R.id.recycler_view), hasMinimumChildCount(1)))

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        openActionBarOverflowOrOptionsMenu(context)
        onView(withText(context.getString(R.string.library_sort_by))).perform(click())
        onView(withText(context.getString(R.string.library_sort_title))).perform(click())

        waitForView(allOf(withId(R.id.recycler_view), hasMinimumChildCount(1)))
        val titles = visibleTitles()

        assertEquals(titles.sortedWith(String.CASE_INSENSITIVE_ORDER), titles)
    }

    private fun visibleTitles(): List<String> {
        val titles = mutableListOf<String>()
        onView(withId(R.id.recycler_view)).check { view, _ ->
            val recyclerView = view as RecyclerView
            for (i in 0 until recyclerView.childCount) {
                recyclerView.getChildAt(i)
                    .findViewById<TextView>(R.id.textViewTitle)
                    ?.let { titles.add(it.text.toString()) }
            }
        }
        return titles
    }
}
