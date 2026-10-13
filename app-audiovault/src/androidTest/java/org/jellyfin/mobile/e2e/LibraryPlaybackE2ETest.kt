package org.jellyfin.mobile.e2e

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import org.jellyfin.mobile.R
import org.junit.Test

/**
 * Browse -> play: after logging in, tapping the first item in the (flattened-by-default) library
 * list starts playback and lands on `PlayerFragment`. Runs against the real local Vault server
 * (see [BaseE2ETest]).
 */
class LibraryPlaybackE2ETest : LoggedInE2ETest() {

    @Test
    fun tappingALibraryItemStartsPlayback() {
        loginAndWaitForLibrary()
        startPlaybackFromFirstLibraryItem()

        onView(playerControl(R.id.exo_play_pause)).check(matches(isDisplayed()))
    }
}
