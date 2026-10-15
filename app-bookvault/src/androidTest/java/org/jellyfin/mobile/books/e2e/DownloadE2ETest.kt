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
import org.jellyfin.mobile.books.downloads.BookDownloads
import org.jellyfin.mobile.common.downloads.ItemDownloads
import org.junit.Assert.assertTrue
import org.junit.Test

// A real download through Media3's DownloadManager from the host machine's Vault
private const val DOWNLOAD_TIMEOUT_MS = 60_000L

/**
 * Long-press -> immediate download (`BookLibraryFragment.showDownloadDialog`; a new download has no
 * confirmation dialog, it starts at once and shows a Toast). Finished means the book's whole file
 * is on the device, not just that the tap registered.
 */
class DownloadE2ETest : LoggedInE2ETest() {

    @Test
    fun downloadingALibraryItemPersistsItLocally() {
        loginAndWaitForLibrary()
        waitForView(allOf(withId(R.id.recycler_view_list), hasMinimumChildCount(1)))
        assertTrue("Expected no downloads before the test", downloadedIds().isEmpty())

        onView(withId(R.id.recycler_view_list))
            .perform(actionOnItemAtPosition<RecyclerView.ViewHolder>(0, longClick()))

        composeRule.waitUntil(timeoutMillis = DOWNLOAD_TIMEOUT_MS) { downloadedIds().isNotEmpty() }
        val file = checkNotNull(onMain { koinGet<ItemDownloads>().fileFor(downloadedIds().single()) })
        val head = ByteArray(2).also { bytes -> file.inputStream().use { it.read(bytes) } }
        assertTrue("Expected an EPUB (PK) or a PDF (%P), got ${String(head)}", String(head) in setOf("PK", "%P"))
    }

    private fun downloadedIds(): Set<String> = runBlocking { koinGet<BookDownloads>().downloadedIds.first() }
}
