package org.jellyfin.mobile.e2e

import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Playback speed is picked from a plain [android.widget.PopupMenu] on the player toolbar (see
 * `PlayerMenus.createSpeedMenu`) rather than a dedicated dialog with stable ids, so items are
 * matched by their rendered text ("1.5x", etc). Verifies the persisted result via [koinGet] rather
 * than the UI, since nothing on screen displays the current speed outside that same popup.
 */
class PlaybackSpeedE2ETest : LoggedInE2ETest() {

    @Test
    fun selectingAPlaybackSpeedPersistsIt() {
        loginAndWaitForLibrary()
        startPlaybackFromFirstLibraryItem()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        openActionBarOverflowOrOptionsMenu(context)
        onView(withText(context.getString(R.string.menu_speed))).perform(click())

        onView(withText("1.5x")).inRoot(isPlatformPopup()).perform(click())

        assertEquals(1.5f, koinGet<AudioVaultAppPreferences>().playbackSpeed)
    }
}
