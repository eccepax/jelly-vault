package org.jellyfin.mobile.books.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.books.BookMainActivity
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.data.BookDatabase
import org.jellyfin.mobile.common.downloads.ItemDownloads
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.testing.endtoend.SignedInE2ETest
import org.jellyfin.mobile.testing.endtoend.SuiteAreaRule
import org.jellyfin.mobile.testing.endtoend.WAIT_TIMEOUT_MS
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Rule
import org.jellyfin.mobile.common.R as CommonR

/**
 * Base for every BookVault e2e test: each starts signed out on a cleared app, and signs in itself.
 * Most get past the connect/login dance with [loginAndWaitForLibrary]; [LoginFlowE2ETest] and
 * [QuickConnectE2ETest] exercise signing in, so nothing here may sign in before a test (no @Before).
 */
abstract class LoggedInE2ETest : SignedInE2ETest<BookMainActivity>(BookMainActivity::class.java) {

    @get:Rule
    val suiteArea = SuiteAreaRule(SUITE_AREAS)

    override fun clearAppState() {
        koinGet<BookDatabase>().clearAllTables()
        // Downloaded files outlive the database tables; without this a later test would find a
        // book already downloaded by an earlier one.
        onMain { koinGet<ItemDownloads>().run { downloads.value.keys.forEach(::remove) } }
        forgetReadingOnServer()
    }

    /**
     * Reading places are kept on the server too (BookProgressSync), and a place read more recently
     * there wins over this device's: without this, one test's place would open the next test's book.
     * Marking a book unplayed clears its place and when it was read.
     */
    private fun forgetReadingOnServer() {
        if (e2eUsername().isBlank() || e2ePassword().isBlank()) return
        asTestUserElsewhere(RESET_DEVICE_ID) { api ->
            val books = api.itemsApi.getItems(includeItemTypes = listOf(BaseItemKind.BOOK), recursive = true)
            books.content.items.orEmpty().mapNotNull { it.id }.forEach { api.playStateApi.markUnplayedItem(it) }
        }
    }

    /** Opens the book titled [title] from the library and waits for the reader to finish loading it. */
    protected fun openBook(title: String) {
        composeRule.onNode(hasTestTag(VaultTestTags.LIBRARY_ITEM) and hasText(title, substring = true)).performClick()
        waitForReaderToFinishLoading()
    }

    /** Leaves the reader for the library. */
    protected fun leaveReader() {
        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.vault_back)).performClick()
        waitForLibraryItems()
    }

    /** Opens the reader's "More options" menu and picks [item] (a string resource). */
    protected fun readerMenu(item: Int) {
        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.vault_more_options)).performClick()
        composeRule.onNodeWithText(context.getString(item)).performClick()
    }

    /** Waits until an EPUB's chapter bar shows chapter [chapter] (from 0); returns the book's chapter count. */
    protected fun waitForChapter(chapter: Int): Int {
        // "Chapter 3 of " in the app's own words: the format with the count left off
        val shown = context.getString(R.string.chapter_position, chapter + 1, 0).removeSuffix("0")
        var count: Int? = null
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            count = composeRule.onAllNodes(hasText(shown, substring = true)).fetchSemanticsNodes()
                .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
                .firstNotNullOfOrNull { it.text.substringAfter(shown, "").toIntOrNull() }
            count != null
        }
        return count!!
    }

    /** Waits until this device has saved the reader on [page] (PDF) or chapter (EPUB), from 0. */
    protected fun waitForSavedPlace(page: Int) {
        composeRule.waitUntil(WAIT_TIMEOUT_MS) {
            runBlocking { koinGet<BookDatabase>().bookProgressDao.getAllProgress() }.any { it.pageNumber == page }
        }
    }

    /** Connects and logs in, returning once the library shows at least one book. */
    protected fun loginAndWaitForLibrary() {
        connectAndSignIn()
        waitForLibraryItems()
    }

    /** Waits until the library shows at least one book. */
    protected fun waitForLibraryItems(timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            screenShows { libraryItems().fetchSemanticsNodes().isNotEmpty() }
        }
    }

    /**
     * [check] of what's on screen, false while there's no screen to check: signing in swaps the
     * activity's content, and for a moment there is none (looking then throws, failing the wait).
     */
    private fun screenShows(check: () -> Boolean): Boolean = runCatching(check).getOrDefault(false)

    /** The books the library has on screen (a lazy list only composes those). */
    protected fun libraryItems() = composeRule.onAllNodesWithTag(VaultTestTags.LIBRARY_ITEM)

    /**
     * Taps the library item at [position] (default: the first) and waits for the reader screen to
     * finish loading it (its spinner going is the reader's own loading signal — see
     * `BookReaderViewModel.loading`).
     */
    protected fun openBookAtPosition(position: Int = 0) {
        libraryItems()[position].performClick()

        waitForReaderToFinishLoading()
    }

    /**
     * Waits for the reader to open and its spinner to go, meaning a page/chapter has rendered. The
     * library has no spinner either, so the reader's own mode button has to show first.
     */
    protected fun waitForReaderToFinishLoading(timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        val pageMode = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.reader_page_mode)
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            screenShows {
                composeRule.onAllNodesWithContentDescription(pageMode).fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithTag(VaultTestTags.LOADING).fetchSemanticsNodes().isEmpty()
            }
        }
    }

    /** Waits until a PDF's page [page] (from 1) has been drawn, not just made room for. */
    protected fun waitForPdfPage(page: Int = 1, timeoutMillis: Long = WAIT_TIMEOUT_MS) {
        val description = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getString(R.string.pdf_page, page)
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            screenShows { composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty() }
        }
    }

    private companion object {
        const val RESET_DEVICE_ID = "vault-e2e-bookvault-reset"
    }
}
