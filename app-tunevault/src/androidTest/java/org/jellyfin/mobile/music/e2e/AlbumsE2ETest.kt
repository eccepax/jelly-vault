package org.jellyfin.mobile.music.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import org.junit.Test

/** The Albums screen lists the real Vault's music albums (and not its audiobooks). */
@BrowseArea
class AlbumsE2ETest : LoggedInE2ETest() {

    @Test
    fun albumsScreenListsMusicAlbums() {
        connectAndLoginToLibrary()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeRule.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("The Purple Bird"))
        composeRule.onNodeWithText("The Purple Bird").assertIsDisplayed()
        composeRule.onNodeWithText("A People's History of the United States", substring = true).assertDoesNotExist()
    }
}
