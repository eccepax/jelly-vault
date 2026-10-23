package org.jellyfin.mobile.books.e2e

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.progress.JellyfinBookProgressServer
import org.jellyfin.mobile.books.progress.pageTicks
import org.jellyfin.mobile.testing.VaultFixtures.Books
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.extensions.ticks
import org.junit.Test
import java.time.Instant
import java.util.UUID

/**
 * The place in a book follows the reader between devices, through the server: the place read most
 * recently, here or elsewhere, wins. "Elsewhere" is the e2e user signed in apart from the app.
 */
@ReadingArea
class ReadingProgressE2ETest : LoggedInE2ETest() {

    @Test
    fun aBookReadFurtherOnAnotherDeviceOpensWhereItWasLeftThere() {
        loginAndWaitForLibrary()
        openBook(Books.EPUB)
        val chapters = waitForChapter(0)
        leaveReader()

        elsewhere { api ->
            val book = epub(api)
            val runTime = requireNotNull(server(api).progress(listOf(book)).getValue(book).runTimeTicks)
            server(api).record(book, pageTicks(READ_ELSEWHERE, chapters, runTime).ticks, false, Instant.now())
        }
        openBook(Books.EPUB)

        waitForChapter(READ_ELSEWHERE)
    }

    @Test
    fun theChapterReadHereIsSentForOtherDevices() {
        loginAndWaitForLibrary()
        openBook(Books.EPUB)
        val chapters = waitForChapter(0)
        repeat(READ_HERE) {
            composeRule.onNodeWithContentDescription(context.getString(R.string.chapter_next)).performClick()
        }
        waitForChapter(READ_HERE)

        // Leaving the book sends its place
        leaveReader()

        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            elsewhere { api ->
                val book = epub(api)
                server(api).progress(listOf(book)).getValue(book).page(chapters) == READ_HERE
            }
        }
    }

    private fun <T> elsewhere(block: suspend (ApiClient) -> T): T = asTestUserElsewhere(OTHER_DEVICE_ID, block)

    private fun server(api: ApiClient) = JellyfinBookProgressServer(api)

    private suspend fun epub(api: ApiClient): UUID = api.itemsApi.getItems(
        includeItemTypes = listOf(BaseItemKind.BOOK),
        recursive = true,
        searchTerm = Books.SEARCH_TERM,
    ).content.items.orEmpty().single().id!!

    private companion object {
        const val OTHER_DEVICE_ID = "vault-e2e-bookvault-other-reader"

        /** The chapter (from 0) read on the other device. */
        const val READ_ELSEWHERE = 5

        /** The chapter (from 0) read here. */
        const val READ_HERE = 2
    }
}
