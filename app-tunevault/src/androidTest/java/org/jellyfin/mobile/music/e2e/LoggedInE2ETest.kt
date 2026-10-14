package org.jellyfin.mobile.music.e2e

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.rule.GrantPermissionRule
import org.jellyfin.mobile.music.TuneMainActivity
import org.jellyfin.mobile.music.data.TuneDatabase
import org.jellyfin.mobile.music.offline.ServerConnectivity
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.testing.endtoend.SignedInE2ETest
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.Rule

/**
 * Base for any e2e test that needs to start from the signed-in library. The connect screen is
 * common's Compose `ConnectScreen`; login is common's classic-View `LoginFragment` (hosted inside
 * TuneVault's Compose tree), so it's driven with Espresso.
 */
abstract class LoggedInE2ETest : SignedInE2ETest<TuneMainActivity>(TuneMainActivity::class.java) {

    /** Pre-granted, so the system's prompt on a first download never covers the screen under test. */
    @get:Rule
    val notifications: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    override fun clearAppState() {
        koinGet<TuneDatabase>().clearAllTables()
        // Playback (a Koin singleton + the PlaybackService) also outlives each test; without this
        // the previous test's queue is still loaded and its mini-player already on screen.
        onMain { koinGet<PlaybackController>().stop() }
        // Online/offline is app-wide too: after a test that took the Vault away, the app only
        // notices it's back at its next check. Starting before then, a test would see the
        // downloads-only library (empty, once downloads are cleared) instead of the server's.
        val connectivity = koinGet<ServerConnectivity>()
        val deadline = System.currentTimeMillis() + ServerConnectivity.RETRY_INTERVAL_MS * 2
        while (connectivity.isOffline.value && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MS)
        check(!connectivity.isOffline.value) { "The app still thinks the Vault is unreachable" }
    }

    /** Connects to the Vault and logs in. Returns once the login has been submitted. */
    protected fun connectAndLogin() = connectAndSignIn()

    /** Signs in (landing on Home) and switches to the Library tab. */
    protected fun connectAndLoginToLibrary() {
        connectAndLogin()
        waitForComposeText("Library")
        composeRule.onNodeWithText("Library").performClick()
        waitForComposeText("Albums")
    }

    /**
     * Scrolls the (first) scrollable list or grid until [text] is composed, then taps it. Lazy lists
     * only compose what's on screen, so off-screen items don't exist until scrolled to.
     */
    protected fun scrollToAndClick(text: String, timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
        composeRule.onNodeWithText(text).performClick()
    }

    /** Opens [title] from the Albums grid and waits for its track list. */
    protected fun openAlbum(title: String, firstTrack: String) {
        scrollToAndClick(title)
        waitForComposeText(firstTrack)
    }
}

private const val POLL_MS = 250L
