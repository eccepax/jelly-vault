package org.jellyfin.mobile.e2e

import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.jellyfin.mobile.MainActivity
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.downloads.ItemDownloads
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.data.JellyfinDatabase
import org.jellyfin.mobile.player.playback.AudiobookPlaybackController
import org.jellyfin.mobile.player.ui.PlayerTags
import org.jellyfin.mobile.testing.endtoend.SignedInE2ETest
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.jellyfin.mobile.common.R as CommonR

/**
 * Base for any e2e test that needs to start from a logged-in library screen — the connect/login
 * dance itself is exercised directly by [LoginFlowE2ETest]; this just gets other flows past it.
 */
abstract class LoggedInE2ETest : SignedInE2ETest<MainActivity>(MainActivity::class.java) {

    override fun clearAppState() {
        // The playback service outlives each test's activity: a book left playing would carry on
        // into the next test
        onMain { koinGet<AudiobookPlaybackController>().stop() }
        koinGet<JellyfinDatabase>().clearAllTables()
        // Downloaded files outlive the database tables; without this a later test would find a
        // track already downloaded by an earlier one.
        onMain { koinGet<ItemDownloads>().run { downloads.value.keys.forEach(::remove) } }
    }

    /** Connects and logs in, returning once the library shows at least one item. */
    protected fun loginAndWaitForLibrary() {
        connectAndSignIn()
        waitForLibraryItems()
    }

    /** Waits until the library shows at least one item. */
    protected fun waitForLibraryItems(timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis) { libraryItems().fetchSemanticsNodes().isNotEmpty() }
    }

    /** The items the library has on screen (a lazy list only composes those). */
    protected fun libraryItems(): SemanticsNodeInteractionCollection =
        composeRule.onAllNodesWithTag(VaultTestTags.LIBRARY_ITEM)

    /** The titles of the items on screen, top to bottom. */
    protected fun libraryTitles(): List<String> = libraryItems().fetchSemanticsNodes().mapNotNull { node ->
        node.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text
    }

    /**
     * Searches the library for [query] and waits until only matching items show. The search is
     * client-side over the loaded list, so this is quick.
     */
    protected fun searchLibrary(query: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onNodeWithContentDescription(context.getString(R.string.search_hint)).performClick()
        composeRule.onNodeWithTag(VaultTestTags.SEARCH_FIELD).performTextInput(query)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            val titles = libraryTitles()
            titles.isNotEmpty() && titles.all { it.contains(query, ignoreCase = true) }
        }
    }

    /** Opens the library's "More options" menu and chooses [label]. */
    protected fun chooseFromLibraryMenu(label: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.vault_more_options)).performClick()
        composeRule.onNodeWithText(label).performClick()
    }

    /**
     * Taps the first library item (always playable, never a browse-only folder, in the default
     * flattened-library mode) and waits for the player. Call [loginAndWaitForLibrary] first.
     */
    protected fun startPlaybackFromFirstLibraryItem() {
        waitForLibraryItems()
        libraryItems()[0].performClick()

        waitForComposeTag(PlayerTags.PLAY_PAUSE)
    }

    /**
     * Waits until the player is actually playing (the button offers pause), not just shown: in the
     * moment after the player opens, its controls are still settling and a tap can't be delivered.
     */
    protected fun waitForPlaying() {
        val pause = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(CommonR.string.vault_pause)
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeRule.onAllNodesWithContentDescription(pause).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The player's position as it reads on screen, such as "1:05". */
    protected fun playerPosition(): String =
        composeRule.onNodeWithTag(PlayerTags.POSITION).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text)?.joinToString("").orEmpty()
}
