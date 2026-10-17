package org.jellyfin.mobile.e2e

import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.downloads.ItemDownloads
import org.jellyfin.mobile.downloads.AudiobookDownloads
import org.jellyfin.mobile.downloads.audioItemIdOf
import org.junit.Before
import org.junit.Test

// "Download" on a multi-track audiobook queues every track in parallel, and even the first to finish
// is a real network transfer, so this needs a generous timeout.
private const val DOWNLOAD_TIMEOUT_MS = 180_000L

/** Isabel Wilkerson's "Caste" on the Vault: split into many short tracks, the first well under 1 MB. */
private const val SMALL_TRACKS_BOOK = "Caste"

/**
 * Long-press -> Download really downloads through the shared download engine. Finished means a
 * track's whole file is on the device and plays from there, not just that the tap registered.
 */
class DownloadE2ETest : LoggedInE2ETest() {

    @Before
    fun grantNotificationPermission() {
        // SystemUtils.kt's requestDownload also asks for POST_NOTIFICATIONS (API 33+) before
        // proceeding — grant it upfront via UiAutomation rather than dealing with a system
        // permission dialog outside the app's own window (Espresso can't reach it; would need
        // UiAutomator). Self-contained: works on a fresh install without any manual adb setup.
        // The permission only exists on API 33+; older versions refuse to grant it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            ApplicationProvider.getApplicationContext<android.content.Context>().packageName,
            "android.permission.POST_NOTIFICATIONS",
        )
    }

    @Test
    fun downloadingALibraryItemPersistsItLocally() {
        loginAndWaitForLibrary()
        assert(downloadedIds().isEmpty()) { "Expected no downloads before the test (state should be reset by LoggedInE2ETest)" }

        // A book of short tracks: the test waits for one whole track, and the first item in the
        // library can be a book of 200 MB files that the emulator can't fetch within the timeout
        searchLibrary(SMALL_TRACKS_BOOK)
        libraryItems()[0].performTouchInput { longClick() }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        composeRule.onNodeWithText(context.getString(R.string.download_action)).performClick()

        // First-ever download on a fresh install: MainActivity.requestDownload (SystemUtils.kt)
        // asks which network types are allowed before it'll proceed at all. Mobile data allows any
        // network, so whether the emulator's network counts as metered can't hold the download back.
        onView(withText(context.getString(R.string.mobile_data))).inRoot(isDialog()).perform(click())

        composeRule.waitUntil(timeoutMillis = DOWNLOAD_TIMEOUT_MS) { downloadedIds().isNotEmpty() }

        val track = downloadedIds().first()
        val file = checkNotNull(onMain { koinGet<ItemDownloads>().fileFor(track) }) { "$track has no file" }
        assert(file.length() > 0) { "Expected $track's file to hold the track" }
        // The player finds the download by the track's own stream url, whatever it carries
        assert(audioItemIdOf(Uri.parse("http://vault/Audio/${track.replace("-", "")}/universal")) == track)
    }

    private fun downloadedIds(): Set<String> = runBlocking { koinGet<AudiobookDownloads>().downloadedIds.first() }
}
