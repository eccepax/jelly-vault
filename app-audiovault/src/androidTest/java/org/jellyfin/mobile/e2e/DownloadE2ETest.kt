package org.jellyfin.mobile.e2e

import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.downloads.ItemDownloads
import org.jellyfin.mobile.downloads.AudiobookDownloads
import org.jellyfin.mobile.downloads.audioItemIdOf
import org.jellyfin.mobile.testing.VaultFixtures.Audiobooks
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.junit.Before
import org.junit.Test
import org.jellyfin.mobile.common.R as CommonR

// "Download" on a multi-track audiobook queues every track in parallel, and even the first to finish
// is a real network transfer, so this needs a generous timeout.
private const val DOWNLOAD_TIMEOUT_MS = 180_000L

/** A book split into many short tracks, the first well under 1 MB. */
private const val SMALL_TRACKS_BOOK = Audiobooks.MULTI_PART_SEARCH_TERM

/**
 * Long-press -> Download really downloads through the shared download engine. Finished means a
 * track's whole file is on the device and plays from there, not just that the tap registered.
 */
@DownloadsArea
class DownloadE2ETest : LoggedInE2ETest() {

    @Before
    fun grantNotificationPermission() {
        // The first download also asks for POST_NOTIFICATIONS (API 33+), once: grant it upfront
        // via UiAutomation rather than dealing with a system permission dialog outside the app's
        // own window (the Compose test APIs can't reach it). Self-contained: works on a fresh
        // install without any manual adb setup.
        // The permission only exists on API 33+; older versions refuse to grant it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            ApplicationProvider.getApplicationContext<android.content.Context>().packageName,
            "android.permission.POST_NOTIFICATIONS",
        )
    }

    @Test
    fun downloadingALibraryItemPersistsItLocally() {
        downloadATrack()

        val track = downloadedIds().first()
        val file = checkNotNull(onMain { koinGet<ItemDownloads>().fileFor(track) }) { "$track has no file" }
        assert(file.length() > 0) { "Expected $track's file to hold the track" }
        // The player finds the download by the track's own stream url, whatever it carries
        assert(audioItemIdOf(Uri.parse("http://vault/Audio/${track.replace("-", "")}/universal")) == track)
    }

    @Test
    fun settingsShowsTheSpaceDownloadsTakeAndRemovesThemAll() {
        downloadATrack()

        chooseFromLibraryMenu(context.getString(R.string.activity_name_settings))
        waitForComposeText(context.getString(CommonR.string.vault_downloads_remove_all))
        composeRule.onNodeWithText(context.getString(CommonR.string.vault_downloads_remove_all)).performScrollTo()
            .performClick()
        composeRule.onNodeWithText(context.getString(CommonR.string.vault_downloads_remove_all_yes)).performScrollTo()
            .performClick()

        composeRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { downloadedIds().isEmpty() }
        waitForComposeText(context.getString(CommonR.string.vault_downloads_nothing))
    }

    /** Signs in and downloads a book of short tracks, until the first track is on the device. */
    private fun downloadATrack() {
        loginAndWaitForLibrary()
        assert(downloadedIds().isEmpty()) { "Expected no downloads before the test (state should be reset by LoggedInE2ETest)" }
        // Downloads use any network unless Settings says Wi-Fi only, so whether the emulator's
        // network counts as metered can't hold this one back

        // A book of short tracks: the test waits for one whole track, and the first item in the
        // library can be a book of 200 MB files that the emulator can't fetch within the timeout
        searchLibrary(SMALL_TRACKS_BOOK)
        libraryItems()[0].performTouchInput { longClick() }
        composeRule.onNodeWithText(context.getString(R.string.download_action)).performClick()

        composeRule.waitUntil(timeoutMillis = DOWNLOAD_TIMEOUT_MS) { downloadedIds().isNotEmpty() }
    }

    private fun downloadedIds(): Set<String> = runBlocking { koinGet<AudiobookDownloads>().downloadedIds.first() }
}
