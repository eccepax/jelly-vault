package org.jellyfin.mobile.books.e2e

import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performTouchInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.books.downloads.BookDownloads
import org.jellyfin.mobile.common.downloads.ItemDownloads
import org.junit.Assert.assertTrue
import org.junit.Test

// A real download through Media3's DownloadManager from the host machine's Vault
private const val DOWNLOAD_TIMEOUT_MS = 60_000L

/**
 * Long-press -> immediate download (a new download has no confirmation dialog, it starts at once
 * and shows a Toast; see `BookLibraryScreen`). Finished means the book's whole file is on the
 * device, not just that the tap registered.
 */
class DownloadE2ETest : LoggedInE2ETest() {

    @Test
    fun downloadingALibraryItemPersistsItLocally() {
        loginAndWaitForLibrary()
        assertTrue("Expected no downloads before the test", downloadedIds().isEmpty())

        libraryItems()[0].performTouchInput { longClick() }

        composeRule.waitUntil(timeoutMillis = DOWNLOAD_TIMEOUT_MS) { downloadedIds().isNotEmpty() }
        val file = checkNotNull(onMain { koinGet<ItemDownloads>().fileFor(downloadedIds().single()) })
        val head = ByteArray(2).also { bytes -> file.inputStream().use { it.read(bytes) } }
        assertTrue("Expected an EPUB (PK) or a PDF (%P), got ${String(head)}", String(head) in setOf("PK", "%P"))
    }

    private fun downloadedIds(): Set<String> = runBlocking { koinGet<BookDownloads>().downloadedIds.first() }
}
