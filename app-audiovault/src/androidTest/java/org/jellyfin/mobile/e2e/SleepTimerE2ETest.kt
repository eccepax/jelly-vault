package org.jellyfin.mobile.e2e

import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.jellyfin.mobile.R
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Player overflow -> Sleep timer (`SleepTimerDialogFragment`, a plain `AlertDialog.setItems`) ->
 * pick a duration. No on-screen player UI reflects the running timer (only the notification does,
 * via `PlayerNotificationHelper` — not checked here) — scoped to confirming the dialog opens, a
 * selection dismisses it, and playback keeps running afterward. Uses the same "find the dialog
 * fragment by tag" technique `PlayerFragmentTest` already uses at the unit level.
 */
class SleepTimerE2ETest : LoggedInE2ETest() {

    @Test
    fun startingASleepTimerDismissesTheDialogAndKeepsPlaybackRunning() {
        loginAndWaitForLibrary()
        startPlaybackFromFirstLibraryItem()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        openActionBarOverflowOrOptionsMenu(context)
        onView(withText(context.getString(R.string.menu_sleep_timer))).perform(click())

        onView(withText(context.getString(R.string.sleep_timer_15_min))).inRoot(isDialog()).perform(click())

        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.activity.supportFragmentManager.findFragmentByTag("SleepTimerDialog") == null
        }
        assertNull(composeRule.activity.supportFragmentManager.findFragmentByTag("SleepTimerDialog"))

        onView(waitForPlayerControl(R.id.exo_play_pause)).check(matches(isDisplayed()))
    }
}
