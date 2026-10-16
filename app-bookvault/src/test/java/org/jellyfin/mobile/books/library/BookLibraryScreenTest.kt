package org.jellyfin.mobile.books.library

import android.app.Application
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.books.app.LibraryViewMode
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class BookLibraryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val dune = book("Dune", author = "Frank Herbert")
    private val kindred = book("Kindred", author = "Octavia E. Butler")
    private val loaded = BookLibraryUiState(books = listOf(dune, kindred))
    private val events = mutableListOf<String>()

    private fun book(name: String, author: String? = null) = BaseItemDto(
        id = UUID.nameUUIDFromBytes(name.toByteArray()),
        name = name,
        albumArtist = author,
        type = BaseItemKind.BOOK,
    )

    private fun show(state: BookLibraryUiState = loaded) {
        composeRule.setContent {
            BookLibraryScreen(
                state = state,
                actions = BookLibraryActions(
                    onOpen = { events += "open ${it.name}" },
                    onDownload = { events += "download ${it.name}" },
                    onRemoveDownload = { events += "remove ${it.name}" },
                    onToggleViewMode = { events += "toggleView" },
                    onQueryChange = { events += "query '$it'" },
                    onSort = { events += "sort $it" },
                    onProgressFilter = { events += "progress $it" },
                    onTagFilter = { events += "tag $it" },
                    onDownloadedOnly = { events += "downloadedOnly $it" },
                    onSettings = { events += "settings" },
                    onRetry = { events += "retry" },
                ),
            )
        }
    }

    private fun openMenuItem(label: String) {
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText(label).performClick()
    }

    // --- The books -------------------------------------------------------------------------------

    @Test
    fun `the list shows each book with its author`() {
        show()

        composeRule.onNodeWithTag(VaultTestTags.LIBRARY_LIST).assertIsDisplayed()
        composeRule.onNodeWithText("Dune").assertIsDisplayed()
        composeRule.onNodeWithText("Frank Herbert").assertIsDisplayed()
        composeRule.onNodeWithText("Octavia E. Butler").assertIsDisplayed()
    }

    @Test
    fun `only downloaded books are marked as downloaded`() {
        show(loaded.copy(downloadedIds = setOf(dune.id.toString())))

        composeRule.onAllNodesWithTag(VaultTestTags.DOWNLOADED_MARK, useUnmergedTree = true)
            .fetchSemanticsNodes().size shouldBe 1
    }

    @Test
    fun `tapping a book opens it`() {
        show()

        composeRule.onNodeWithText("Kindred").performClick()

        events shouldBe listOf("open Kindred")
    }

    @Test
    fun `long-pressing a book that isn't downloaded downloads it`() {
        show()

        composeRule.onNodeWithText("Dune").performTouchInput { longClick() }

        events shouldBe listOf("download Dune")
    }

    @Test
    fun `long-pressing a downloaded book asks before removing it`() {
        show(loaded.copy(downloadedIds = setOf(dune.id.toString())))

        composeRule.onNodeWithText("Dune").performTouchInput { longClick() }
        events shouldBe emptyList()
        composeRule.onNodeWithText("Remove download").performClick()

        events shouldBe listOf("remove Dune")
    }

    @Test
    fun `the shelf shows the books as a grid`() {
        show(loaded.copy(viewMode = LibraryViewMode.SHELF))

        composeRule.onNodeWithTag(VaultTestTags.LIBRARY_GRID).assertIsDisplayed()
        composeRule.onNodeWithTag(VaultTestTags.LIBRARY_LIST).assertDoesNotExist()
        composeRule.onNodeWithText("Dune").performClick()

        events shouldBe listOf("open Dune")
    }

    @Test
    fun `the view button switches between the list and the shelf`() {
        show()

        composeRule.onNodeWithContentDescription("Show as a shelf").performClick()

        events shouldBe listOf("toggleView")
    }

    // --- Loading, empty, error -------------------------------------------------------------------

    @Test
    fun `while the first books load, it shows progress`() {
        show(BookLibraryUiState(loading = true))

        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
    }

    @Test
    fun `with no books, it says so`() {
        show(BookLibraryUiState())

        composeRule.onNodeWithText("No books to show").assertIsDisplayed()
    }

    @Test
    fun `when the books fail to load, it says why and can try again`() {
        show(BookLibraryUiState(error = "Not connected to a server"))

        composeRule.onNodeWithText("Not connected to a server").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        events shouldBe listOf("retry")
    }

    // --- Search ----------------------------------------------------------------------------------

    @Test
    fun `search opens a box that filters as you type`() {
        show()

        composeRule.onNodeWithContentDescription("Search").performClick()
        composeRule.onNodeWithTag(VaultTestTags.SEARCH_FIELD).performTextInput("Kin")

        events shouldBe listOf("query 'Kin'")
    }

    @Test
    fun `closing search clears it`() {
        show(loaded.copy(query = "Kin"))

        composeRule.onNodeWithContentDescription("Close search").performClick()

        events shouldBe listOf("query ''")
        composeRule.onNodeWithTag(VaultTestTags.SEARCH_FIELD).assertDoesNotExist()
    }

    // --- Sorting and filtering -------------------------------------------------------------------

    @Test
    fun `sort offers each order with the current one selected`() {
        show(loaded.copy(sort = BookSortOption.AUTHOR))

        openMenuItem("Sort by")
        composeRule.onNode(hasText("Author") and isSelectable()).assertIsSelected()
        composeRule.onNodeWithText("Date Added").performClick()

        events shouldBe listOf("sort DATE_ADDED")
    }

    @Test
    fun `progress filter offers each state with the current one selected`() {
        show(loaded.copy(progressFilter = ProgressFilter.UNREAD))

        openMenuItem("Filter by progress")
        composeRule.onNode(hasText("Unread") and isSelectable()).assertIsSelected()
        composeRule.onNodeWithText("Finished").performClick()

        events shouldBe listOf("progress FINISHED")
    }

    @Test
    fun `filtering by tag offers every tag in the library, after all tags, with the current one selected`() {
        show(loaded.copy(availableTags = listOf("Fantasy", "Sci-Fi"), tagFilter = "Sci-Fi"))

        openMenuItem("Filter by tag")

        composeRule.onAllNodes(isSelectable()).fetchSemanticsNodes().map { node ->
            node.config.getOrNull(SemanticsProperties.Text)
                ?.joinToString()
        } shouldBe listOf("All tags", "Fantasy", "Sci-Fi")
        composeRule.onNode(hasText("Sci-Fi") and isSelectable()).assertIsSelected()
    }

    @Test
    fun `choosing a tag narrows the library to it, and all tags shows every book again`() {
        show(loaded.copy(availableTags = listOf("Fantasy", "Sci-Fi"), tagFilter = "Sci-Fi"))

        openMenuItem("Filter by tag")
        composeRule.onNodeWithText("Fantasy").performClick()
        openMenuItem("Filter by tag")
        composeRule.onNodeWithText("All tags").performClick()

        events shouldBe listOf("tag Fantasy", "tag null")
    }

    @Test
    fun `with no tags in the library, filtering by tag says so`() {
        show(loaded.copy(availableTags = emptyList()))

        openMenuItem("Filter by tag")

        composeRule.onNodeWithText(
            "None of your books have tags yet. Add tags to books in Jellyfin to filter by them here.",
        ).assertIsDisplayed()
    }

    @Test
    fun `downloaded only shows whether it's on, and switches it`() {
        show(loaded.copy(downloadedOnly = true))

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithTag(VaultTestTags.DOWNLOADED_ONLY, useUnmergedTree = true).assertIsOn()
        composeRule.onNodeWithText("Downloaded only").performClick()

        events shouldBe listOf("downloadedOnly false")
    }

    @Test
    fun `downloaded only is off by default`() {
        show()

        composeRule.onNodeWithContentDescription("More options").performClick()

        composeRule.onNodeWithTag(VaultTestTags.DOWNLOADED_ONLY, useUnmergedTree = true).assertIsOff()
    }

    @Test
    fun `settings is in the menu`() {
        show()

        openMenuItem("Settings")

        events shouldBe listOf("settings")
    }
}
