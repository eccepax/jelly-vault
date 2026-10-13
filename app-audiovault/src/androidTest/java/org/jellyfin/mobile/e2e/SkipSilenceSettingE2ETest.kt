package org.jellyfin.mobile.e2e

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.RecyclerViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Matchers.allOf
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Library toolbar overflow -> Settings -> "Skip silence" checkbox. Same `hasSibling`-scoped
 * `R.id.recycler_view` disambiguation as [LogoutE2ETest] (the library screen stays attached
 * underneath). Verified via [koinGet] rather than re-opening Settings to inspect the checkbox's
 * rendered state, since that state isn't otherwise easily readable from a single Espresso check.
 */
class SkipSilenceSettingE2ETest : LoggedInE2ETest() {

    @Test
    fun togglingSkipSilencePersistsThePreference() {
        loginAndWaitForLibrary()
        assertTrue(!koinGet<AudioVaultAppPreferences>().skipSilenceEnabled)

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        openActionBarOverflowOrOptionsMenu(context)
        onView(withText(context.getString(R.string.activity_name_settings))).perform(click())

        val settingsRecyclerView = allOf(
            withId(R.id.recycler_view),
            hasSibling(hasDescendant(withText(context.getString(R.string.activity_name_settings)))),
        )
        val skipSilenceTitle = context.getString(R.string.pref_skip_silence_title)
        onView(settingsRecyclerView).perform(
            scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(skipSilenceTitle))),
        )
        onView(withText(skipSilenceTitle)).perform(click())

        assertTrue(koinGet<AudioVaultAppPreferences>().skipSilenceEnabled)
    }
}
