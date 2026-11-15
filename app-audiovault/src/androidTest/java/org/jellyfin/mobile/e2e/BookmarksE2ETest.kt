package org.jellyfin.mobile.e2e

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.jellyfin.mobile.R
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.Test
import org.jellyfin.mobile.common.R as CommonR

/**
 * Bookmarks a place in the first book against the test server, plays on past it, and goes back to
 * it from the list. Bookmarks are kept on the device, cleared between tests.
 */
@PlaybackArea
class BookmarksE2ETest : LoggedInE2ETest() {

    @Test
    fun aBookmarkTakesTheBookBackToItsPlace() {
        loginAndWaitForLibrary()
        startPlaybackFromFirstLibraryItem()
        waitForPlaying()
        tap(R.string.player_fast_forward)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { seconds(playerPosition()) >= SKIP_SECONDS }

        chooseFromPlayerMenu(CommonR.string.add_bookmark_action)
        composeRule.onNodeWithText(context.getString(CommonR.string.add_bookmark_note_hint)).performTextInput("here")
        composeRule.onNodeWithText(context.getString(CommonR.string.bookmark_save)).performClick()
        val marked = seconds(playerPosition())

        // On into the next part
        tap(R.string.player_fast_forward)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { seconds(playerPosition()) < marked }

        chooseFromPlayerMenu(CommonR.string.bookmarks_title)
        composeRule.onNodeWithText("here", substring = true).performClick()
        composeRule.waitUntil(WAIT_TIMEOUT_MS) { seconds(playerPosition()) in marked - 2..marked + 5 }
    }

    private fun chooseFromPlayerMenu(label: Int) {
        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.vault_more_options)).performClick()
        composeRule.onNodeWithText(context.getString(label)).performClick()
    }

    private fun tap(description: Int) =
        composeRule.onNodeWithContentDescription(context.getString(description)).performClick()

    private fun seconds(position: String): Int =
        position.split(':').fold(0) { total, part -> total * SECONDS_PER_MINUTE + part.trim().toInt() }

    private companion object {
        const val SKIP_SECONDS = 30
        const val SECONDS_PER_MINUTE = 60
    }
}
