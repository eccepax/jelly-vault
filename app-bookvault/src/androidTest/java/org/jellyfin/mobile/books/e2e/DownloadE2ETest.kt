package org.jellyfin.mobile.books.e2e

import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.longClick
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount
import androidx.test.espresso.matcher.ViewMatchers.withId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matchers.allOf
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.data.BookDatabase
import org.junit.Test

// BookDownloadManager does a plain synchronous OkHttp GET-to-file (no Media3 DownloadService, no
// background queue) over the emulator's connection to the host machine's Vault — much faster than
// AudioVault's multi-track Media3 download, but still a real network+disk round trip.
private const val DOWNLOAD_TIMEOUT_MS = 30_000L

/**
 * Long-press -> immediate download (`BookLibraryFragment.showDownloadDialog`, already exercised at
 * the unit level in `BookLibraryFragmentTest` — unlike `app-audiovault`, a *new* download has no
 * confirmation dialog: it fires immediately and shows a `Toast`). Completion is detected by polling
 * `DownloadRecordDao` directly via [koinGet] — `BookLibraryViewModel`'s `downloadedIds` StateFlow
 * is sourced from the same table, so a non-empty result is a reliable signal the download actually
 * wrote a `DownloadRecord`, not just that the tap was registered.
 */
class DownloadE2ETest : LoggedInE2ETest() {

    @Test
    fun downloadingALibraryItemPersistsItLocally() {
        loginAndWaitForLibrary()
        waitForView(allOf(withId(R.id.recycler_view_list), hasMinimumChildCount(1)))
        assert(downloadCount() == 0) { "Expected no downloads before the test (state should be reset by BaseE2ETest)" }

        onView(withId(R.id.recycler_view_list))
            .perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(0, longClick()))

        composeRule.waitUntil(timeoutMillis = DOWNLOAD_TIMEOUT_MS) { downloadCount() > 0 }

        assert(downloadCount() > 0) { "Expected at least one DownloadRecord row after the download completed" }
    }

    private fun downloadCount(): Int = runBlocking { koinGet<BookDatabase>().downloadRecordDao.getAll().first().size }
}
