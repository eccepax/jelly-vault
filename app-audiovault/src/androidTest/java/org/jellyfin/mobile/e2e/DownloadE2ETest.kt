package org.jellyfin.mobile.e2e

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.dao.DownloadDao
import org.junit.Before
import org.junit.Test

// "Download" on a multi-track audiobook queues every track in parallel, and even the first to finish
// is a real network transfer, so this needs a generous timeout.
private const val DOWNLOAD_TIMEOUT_MS = 180_000L

/**
 * Long-press -> Download really downloads through Media3's `JellyfinDownloadService`. Completion is
 * detected by polling `DownloadDao` (a row is only written once a file is fully on disk), since no
 * downloads screen is reachable once logged in.
 */
class DownloadE2ETest : LoggedInE2ETest() {

    @Before
    fun grantNotificationPermission() {
        // SystemUtils.kt's requestDownload also asks for POST_NOTIFICATIONS (API 33+) before
        // proceeding — grant it upfront via UiAutomation rather than dealing with a system
        // permission dialog outside the app's own window (Espresso can't reach it; would need
        // UiAutomator). Self-contained: works on a fresh install without any manual adb setup.
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            ApplicationProvider.getApplicationContext<android.content.Context>().packageName,
            "android.permission.POST_NOTIFICATIONS",
        )
    }

    @Test
    fun downloadingALibraryItemPersistsItLocally() {
        loginAndWaitForLibrary()
        waitForView(allOf(withId(R.id.recycler_view), hasMinimumChildCount(1)))
        assert(downloadCount() == 0) { "Expected no downloads before the test (state should be reset by BaseE2ETest)" }

        onView(withId(R.id.recycler_view))
            .perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(0, longClick()))

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        onView(withText(context.getString(R.string.download_action))).inRoot(isDialog()).perform(click())

        // First-ever download on a fresh install: MainActivity.requestDownload (SystemUtils.kt)
        // asks which network types are allowed before it'll proceed at all. Pick the most
        // permissive option so the emulator's virtual network's actual metered/roaming status
        // (unpredictable) can't fail DownloadUtils.checkForDownloadMethod's connection check.
        onView(withText(context.getString(R.string.mobile_data_and_roaming))).inRoot(isDialog()).perform(click())

        composeRule.waitUntil(timeoutMillis = DOWNLOAD_TIMEOUT_MS) { downloadCount() > 0 }

        assert(downloadCount() > 0) { "Expected at least one DownloadEntity row after the download completed" }
    }

    private fun downloadCount(): Int = runBlocking { koinGet<DownloadDao>().getAllDownloads().first().size }
}
