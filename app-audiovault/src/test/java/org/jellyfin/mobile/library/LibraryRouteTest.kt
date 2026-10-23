package org.jellyfin.mobile.library

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.downloads.DownloadRequests
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.playback.AudiobookTrack
import org.jellyfin.mobile.player.playback.FakeAudiobookController
import org.jellyfin.mobile.robolectric.playerViewModel
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import org.jellyfin.mobile.common.R as CommonR

/** The library over a stand-in [LibraryViewModel]: what tapping and holding items does, and the mini player. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LibraryRouteTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val libraryViewModel = mockk<LibraryViewModel>(relaxed = true)
    private val playback = FakeAudiobookController()
    private val playerViewModel = playerViewModel(playback)

    private val itemsFlow = MutableStateFlow<List<LibraryItem>>(emptyList())
    private val downloadedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val hiddenIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val filterQueryFlow = MutableStateFlow("")
    private val canGoUpFlow = MutableStateFlow(false)

    private val played = mutableListOf<PlayOptions>()
    private val downloaded = mutableListOf<List<String>>()
    private val removed = mutableListOf<List<String>>()
    private var playerOpened = 0
    private var settingsOpened = 0

    private val downloads = object : DownloadRequests {
        override fun download(trackIds: List<String>) {
            downloaded += trackIds
        }

        override fun remove(trackIds: List<String>) {
            removed += trackIds
        }
    }

    init {
        every { libraryViewModel.items } returns itemsFlow
        every { libraryViewModel.downloadedIds } returns downloadedIdsFlow
        every { libraryViewModel.hiddenIds } returns hiddenIdsFlow
        every { libraryViewModel.filterQuery } returns filterQueryFlow
        every { libraryViewModel.title } returns MutableStateFlow("Library")
        every { libraryViewModel.canGoUp } returns canGoUpFlow
        every { libraryViewModel.isLoading } returns MutableStateFlow(false)
        every { libraryViewModel.error } returns MutableStateFlow(null)
        every { libraryViewModel.sortOption } returns MutableStateFlow(SortOption.TITLE)
        every { libraryViewModel.showDownloadedOnly } returns MutableStateFlow(false)
        every { libraryViewModel.showHiddenItems } returns MutableStateFlow(false)
    }

    @Composable
    private fun Library() {
        AppTheme(VaultBrand.AudioVault) {
            LibraryRoute(
                onPlay = { played += it },
                onOpenPlayer = { playerOpened++ },
                onOpenSettings = { settingsOpened++ },
                viewModel = libraryViewModel,
                playerViewModel = playerViewModel,
                downloads = downloads,
                coverUrl = { _, _ -> null },
            )
        }
    }

    private fun show() = composeRule.setContent { Library() }

    private fun audioFile(id: UUID = UUID.randomUUID(), name: String = "Track") =
        LibraryItem.AudioFile(BaseItemDto(id = id, type = BaseItemKind.AUDIO_BOOK, name = name))

    private fun string(resId: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(resId)

    private fun holdDown(name: String) = composeRule.onNodeWithText(name).performTouchInput { longClick() }

    private fun pressBack() = composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun `with nothing playing there's no mini player`() {
        show()

        composeRule.onNodeWithTag(VaultTestTags.MINI_PLAYER).assertDoesNotExist()
    }

    @Test
    fun `once a book is loaded, the mini player shows it, and tapping it opens the player`() {
        show()

        playback.playing(AudiobookTrack(id = UUID.randomUUID().toString(), title = "Dune", durationMs = 0L))
        composeRule.onNodeWithText("Dune").performClick()

        composeRule.onNodeWithTag(VaultTestTags.MINI_PLAYER).assertExists()
        playerOpened shouldBe 1
    }

    @Test
    fun `the mini player's stop stops the book, and the list is loaded again for where it got to`() {
        playback.playing(AudiobookTrack(id = UUID.randomUUID().toString(), title = "Dune", durationMs = 0L))
        show()

        composeRule.onNodeWithContentDescription(string(R.string.notification_action_stop)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(VaultTestTags.MINI_PLAYER).assertDoesNotExist()
        playback.commands shouldBe listOf("stop")
        verify { libraryViewModel.onPlaybackStopped() }
    }

    /** As the player does, opened over the library: the library isn't there to see the book stop. */
    @Test
    fun `a book that stopped while the library was covered has the list loaded again once it's back`() {
        var covered by mutableStateOf(false)
        playback.playing(AudiobookTrack(id = UUID.randomUUID().toString(), title = "Dune", durationMs = 0L))
        composeRule.setContent {
            val savedStates = rememberSaveableStateHolder()
            if (covered) Text("Player") else savedStates.SaveableStateProvider("library") { Library() }
        }
        covered = true
        composeRule.waitForIdle()

        playback.stop()
        composeRule.waitForIdle()
        verify(exactly = 0) { libraryViewModel.onPlaybackStopped() }
        covered = false
        composeRule.waitForIdle()

        verify(exactly = 1) { libraryViewModel.onPlaybackStopped() }
    }

    @Test
    fun `tapping an audio file plays it from where it was left`() {
        val item = audioFile(name = "Dune")
        itemsFlow.value = listOf(item)
        val queue = LibraryViewModel.AudioQueue(listOf(item.id), 0, listOf("Dune"), null)
        coEvery { libraryViewModel.getAudioQueueWithResume(item.id) } returns queue
        show()

        composeRule.onNodeWithText("Dune").performClick()
        composeRule.waitForIdle()

        played.single().ids shouldBe listOf(item.id)
    }

    @Test
    fun `holding a not-yet-downloaded audio file offers to download it`() {
        val item = audioFile(name = "Dune")
        itemsFlow.value = listOf(item)
        show()

        holdDown("Dune")
        composeRule.onNodeWithText(string(R.string.download_action)).performClick()

        downloaded shouldBe listOf(listOf(item.id.toString()))
    }

    @Test
    fun `holding an already-downloaded audio file offers to remove it`() {
        val item = audioFile(name = "Dune")
        itemsFlow.value = listOf(item)
        downloadedIdsFlow.value = setOf(item.id.toString())
        show()

        holdDown("Dune")
        composeRule.onNodeWithText(string(R.string.download_action)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.remove_download_action)).performClick()

        removed shouldBe listOf(listOf(item.id.toString()))
    }

    @Test
    fun `holding an item offers to hide it, and hiding it hides it`() {
        val item = audioFile(name = "Dune")
        itemsFlow.value = listOf(item)
        show()

        holdDown("Dune")
        composeRule.onNodeWithText(string(R.string.hide_action)).performClick()

        verify { libraryViewModel.toggleHidden(item.id) }
    }

    @Test
    fun `holding an already-hidden item offers to unhide it`() {
        val item = audioFile(name = "Dune")
        itemsFlow.value = listOf(item)
        hiddenIdsFlow.value = setOf(item.id.toString())
        show()

        holdDown("Dune")

        composeRule.onNodeWithText(string(R.string.unhide_action)).assertExists()
    }

    @Test
    fun `holding a browse folder opens it instead of offering a menu`() {
        val folder = LibraryItem.BrowseFolder(
            BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.FOLDER, name = "Series", isFolder = true),
        )
        itemsFlow.value = listOf(folder)
        show()

        holdDown("Series")

        composeRule.onNodeWithText(string(R.string.hide_action)).assertDoesNotExist()
        verify { libraryViewModel.openFolder(folder.id, folder.displayName) }
    }

    @Test
    fun `the menu opens settings`() {
        show()

        composeRule.onNode(hasContentDescription(string(CommonR.string.vault_more_options))).performClick()
        composeRule.onNodeWithText(string(R.string.activity_name_settings)).performClick()

        settingsOpened shouldBe 1
    }

    @Test
    fun `in a folder, back goes up a level`() {
        canGoUpFlow.value = true
        show()

        pressBack()

        verify { libraryViewModel.goUp() }
    }

    @Test
    fun `at the top, back is left to the app`() {
        show()

        pressBack()

        verify(exactly = 0) { libraryViewModel.goUp() }
    }

    @Test
    fun `coming back to the library reloads it if a setting it shows changed`() {
        show()
        composeRule.waitForIdle()

        verify { libraryViewModel.checkAndReloadIfSettingChanged() }
    }

    @Test
    fun `after a rotation the search box still shows the search filtering the list`() {
        filterQueryFlow.value = "potter"
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { Library() }
        composeRule.onNodeWithText("potter").assertExists()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("potter").assertExists()
    }

    @Test
    fun `when the search is cleared, the box empties too`() {
        filterQueryFlow.value = "potter"
        show()

        // As opening a folder does
        filterQueryFlow.value = ""

        composeRule.onNodeWithText("potter").assertDoesNotExist()
    }
}
