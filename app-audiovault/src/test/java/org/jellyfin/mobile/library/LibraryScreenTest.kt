package org.jellyfin.mobile.library

import android.app.Application
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
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
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import org.jellyfin.mobile.common.R as CommonR

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LibraryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private val actions = LibraryActions(
        onUp = { events += "up" },
        onQueryChange = { events += "query $it" },
        onSort = { events += "sort $it" },
        onDownloadedOnly = { events += "downloadedOnly $it" },
        onShowHidden = { events += "showHidden $it" },
        onSettings = { events += "settings" },
        onRefresh = { events += "refresh" },
        onOpen = { events += "open ${it.displayName}" },
        onLongPress = { events += "longPress ${it.displayName}" },
        onDismissMenu = { events += "dismissMenu" },
        onOpenPlayer = { events += "openPlayer" },
        onPlayPause = { events += "playPause" },
        onStop = { events += "stop" },
    )

    private fun book(name: String, author: String? = null, ticks: Long? = null, played: Double? = null) =
        LibraryItem.AudioFile(
            BaseItemDto(
                id = UUID.randomUUID(),
                type = BaseItemKind.AUDIO_BOOK,
                name = name,
                albumArtist = author,
                runTimeTicks = ticks,
                userData = played?.let { userData(it) },
            ),
        )

    private fun userData(played: Double) = UserItemDataDto(
        playedPercentage = played,
        playbackPositionTicks = 0,
        playCount = 0,
        isFavorite = false,
        played = false,
        key = "",
        itemId = UUID.randomUUID(),
    )

    private val dune = book("Dune", author = "Frank Herbert", ticks = HOURS_2 + MINUTES_5, played = 40.0)
    private val kindred = book("Kindred")
    private val folder = LibraryItem.BrowseFolder(
        BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.FOLDER, name = "Series", isFolder = true),
    )

    private val defaults = LibraryUiState(title = "All Audiobooks", items = listOf(dune, kindred, folder))

    private fun show(state: LibraryUiState = defaults) {
        composeRule.setContent { AppTheme(VaultBrand.AudioVault) { LibraryScreen(state, actions) } }
    }

    private fun string(resId: Int) = ApplicationProvider.getApplicationContext<Application>().getString(resId)

    private fun openMenu() =
        composeRule.onNodeWithContentDescription(string(CommonR.string.vault_more_options)).performClick()

    // --- the list -----------------------------------------------------------------------------------

    @Test
    fun `each book shows its title, author, length and how far through it is`() {
        show()

        composeRule.onNodeWithText("All Audiobooks").assertExists()
        composeRule.onNodeWithText("Dune").assertExists()
        composeRule.onNodeWithText("Frank Herbert · 2h 5m · 40% played").assertExists()
        composeRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.4f, 0f..1f))).assertExists()
    }

    @Test
    fun `tapping an item opens it, and holding it asks what to do`() {
        show()

        composeRule.onNodeWithText("Kindred").performClick()
        composeRule.onNodeWithText("Dune").performTouchInput { longClick() }

        events shouldBe listOf("open Kindred", "longPress Dune")
    }

    @Test
    fun `only downloaded books are marked as downloaded`() {
        show(defaults.copy(downloadedIds = setOf(dune.id.toString())))

        composeRule.onAllNodesWithTag(VaultTestTags.DOWNLOADED_MARK, useUnmergedTree = true)
            .fetchSemanticsNodes().size shouldBe 1
    }

    @Test
    fun `hidden items, when shown, are marked as hidden`() {
        show(defaults.copy(hiddenIds = setOf(kindred.id.toString()), showHidden = true))

        composeRule.onNodeWithContentDescription(string(R.string.hidden_item_desc)).assertExists()
    }

    @Test
    fun `a download finishing redraws only its own row`() {
        val downloaded = mutableStateOf(emptySet<String>())
        val drawn = mutableMapOf<String, Int>()
        composeRule.setContent {
            AppTheme(VaultBrand.AudioVault) {
                LibraryList(
                    items = listOf(dune, kindred),
                    downloadedIds = downloaded.value,
                    hiddenIds = emptySet(),
                    actions = actions,
                    row = { item, isDownloaded, isHidden -> CountedRow(item, isDownloaded, isHidden, drawn) },
                )
            }
        }
        composeRule.waitForIdle()

        downloaded.value = setOf(dune.id.toString())
        composeRule.waitForIdle()

        drawn shouldBe mapOf("Dune" to 2, "Kindred" to 1)
    }

    @Test
    fun `an item coming back above the top of the list is shown, not scrolled past`() {
        // Like un-hiding the first book: the list, longer than the screen, was at its top, and a new
        // first item arrives
        val rest = listOf(kindred) + (1..LONG_LIST).map { book("Book $it") }
        val items = mutableStateOf(rest)
        composeRule.setContent {
            AppTheme(VaultBrand.AudioVault) {
                LibraryList(items = items.value, downloadedIds = emptySet(), hiddenIds = emptySet(), actions = actions)
            }
        }
        composeRule.waitForIdle()

        items.value = listOf(dune) + rest

        composeRule.onNodeWithText("Dune").assertIsDisplayed()
    }

    // --- top bar ------------------------------------------------------------------------------------

    @Test
    fun `inside a folder, back goes up a level`() {
        show(defaults.copy(canGoUp = true))

        composeRule.onNodeWithContentDescription(string(CommonR.string.vault_back)).performClick()

        events shouldBe listOf("up")
    }

    @Test
    fun `at the top there's no back button`() {
        show()

        composeRule.onNodeWithContentDescription(string(CommonR.string.vault_back)).assertDoesNotExist()
    }

    @Test
    fun `search opens a box that filters as you type, and closing it clears it`() {
        show()

        composeRule.onNodeWithContentDescription(string(R.string.search_hint)).performClick()
        composeRule.onNodeWithTag(VaultTestTags.SEARCH_FIELD).performTextInput("Dune")
        composeRule.onNodeWithContentDescription(string(R.string.close_search)).performClick()

        events shouldBe listOf("query Dune", "query ")
    }

    @Test
    fun `the menu opens settings`() {
        show()

        openMenu()
        composeRule.onNodeWithText(string(R.string.activity_name_settings)).performClick()

        events shouldBe listOf("settings")
    }

    @Test
    fun `downloaded only and show hidden say whether they're on, and switch`() {
        show(defaults.copy(downloadedOnly = true))

        openMenu()
        composeRule.onNodeWithText(string(R.string.filter_downloaded_only)).assertIsOn()
        composeRule.onNodeWithText(string(R.string.show_hidden_items)).assertIsOff()
        composeRule.onNodeWithText(string(R.string.filter_downloaded_only)).performClick()
        openMenu()
        composeRule.onNodeWithText(string(R.string.show_hidden_items)).performClick()

        events shouldBe listOf("downloadedOnly false", "showHidden true")
    }

    @Test
    fun `sort offers each order with the current one selected`() {
        show(defaults.copy(sort = SortOption.AUTHOR))

        openMenu()
        composeRule.onNodeWithText(string(R.string.library_sort_by)).performClick()
        composeRule.onNode(hasText(string(R.string.library_sort_author)) and isSelectable()).assertIsSelected()
        composeRule.onNode(hasText(string(R.string.library_sort_title)) and isSelectable()).assertIsNotSelected()
        composeRule.onNodeWithText(string(R.string.library_sort_last_played)).performClick()

        events shouldBe listOf("sort ${SortOption.LAST_PLAYED}")
    }

    // --- loading, errors, empty --------------------------------------------------------------------

    @Test
    fun `while the first items load, it shows progress`() {
        show(defaults.copy(items = emptyList(), loading = true))

        composeRule.onNodeWithTag(VaultTestTags.LOADING).assertExists()
    }

    @Test
    fun `when the library fails to load, it says why and can try again`() {
        show(defaults.copy(items = emptyList(), error = "Server unreachable"))

        composeRule.onNodeWithText("Server unreachable").assertExists()
        composeRule.onNodeWithText(string(CommonR.string.vault_retry)).performClick()

        events shouldBe listOf("refresh")
    }

    @Test
    fun `with nothing to show, it says so`() {
        show(defaults.copy(items = emptyList()))

        composeRule.onNodeWithText(string(R.string.library_empty)).assertExists()
    }

    // --- the long-press menu -----------------------------------------------------------------------

    @Test
    fun `the item menu lists its actions, and choosing one does it and closes`() {
        var downloaded = 0
        val menu = ItemMenu("Dune", listOf(ItemAction(string(R.string.download_action)) { downloaded++ }))
        show(defaults.copy(itemMenu = menu))

        composeRule.onNodeWithText(string(R.string.download_action)).performClick()

        downloaded shouldBe 1
        events shouldBe listOf("dismissMenu")
    }

    // --- the mini player ----------------------------------------------------------------------------

    @Test
    fun `while something plays, the mini player shows it and controls it`() {
        show(defaults.copy(nowPlaying = NowPlayingBar(title = "Chapter 3", coverUrl = null, isPlaying = true)))

        composeRule.onNodeWithText("Chapter 3").performClick()
        composeRule.onNodeWithContentDescription(string(CommonR.string.vault_pause)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.notification_action_stop)).performClick()

        events shouldBe listOf("openPlayer", "playPause", "stop")
    }

    @Test
    fun `with nothing playing there's no mini player`() {
        show()

        composeRule.onNodeWithTag(VaultTestTags.MINI_PLAYER).assertDoesNotExist()
    }

    private companion object {
        const val LONG_LIST = 40
        const val TICKS_PER_MINUTE = 600_000_000L
        const val MINUTES_5 = 5 * TICKS_PER_MINUTE
        const val HOURS_2 = 120 * TICKS_PER_MINUTE
    }
}

/** Stands in for a library row, as a composable of its own like the real one, counting each time it's drawn. */
@Composable
private fun CountedRow(item: LibraryItem, downloaded: Boolean, hidden: Boolean, drawn: MutableMap<String, Int>) {
    drawn[item.displayName] = (drawn[item.displayName] ?: 0) + 1
    Text("${item.displayName} $downloaded $hidden")
}
