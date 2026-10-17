package org.jellyfin.mobile.music.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import org.jellyfin.mobile.testing.VaultFixtures.Audiobooks
import org.jellyfin.mobile.testing.VaultFixtures.PatternSuite
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.Test

/** The Albums screen lists the test server's music albums (and not its audiobooks). */
@BrowseArea
class AlbumsE2ETest : LoggedInE2ETest() {

    @Test
    fun albumsScreenListsMusicAlbums() {
        connectAndLoginToLibrary()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeRule.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(PatternSuite.TITLE))
        composeRule.onNodeWithText(PatternSuite.TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(Audiobooks.TITLE_MATCHING_SEARCH_TERM, substring = true).assertDoesNotExist()
    }
}
