package org.jellyfin.mobile.e2e

import androidx.compose.ui.test.onNodeWithTag
import org.jellyfin.mobile.player.ui.PlayerTags
import org.junit.Test

/**
 * Browse -> play: after logging in, tapping the first item in the (flattened-by-default) library
 * list starts playback and lands on the player (`PlayerRoute`). Runs against the test server
 * (see [BaseE2ETest]).
 */
@PlaybackArea
class LibraryPlaybackE2ETest : LoggedInE2ETest() {

    @Smoke
    @Test
    fun tappingALibraryItemStartsPlayback() {
        loginAndWaitForLibrary()
        startPlaybackFromFirstLibraryItem()

        composeRule.onNodeWithTag(PlayerTags.PLAY_PAUSE).assertExists()
    }
}
