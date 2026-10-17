package org.jellyfin.mobile.library

import android.app.Application
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.MutableLiveData
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.robolectric.mockedPlayerViewModel
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import org.jellyfin.mobile.common.R as CommonR

/**
 * Tests the audiobook-download long-press menu and tap-to-play, with every dependency mocked through
 * a Koin test module so no network or database is touched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LibraryFragmentTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val libraryViewModel = mockk<LibraryViewModel>(relaxed = true)
    private val activityEventHandler = mockk<ActivityEventHandler>(relaxed = true)
    private val apiClient = mockk<ApiClient>(relaxed = true)
    private val playerViewModel = mockedPlayerViewModel()

    private val itemsFlow = MutableStateFlow<List<LibraryItem>>(emptyList())
    private val downloadedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val hiddenIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val filterQueryFlow = MutableStateFlow("")

    @Before
    fun setUp() {
        every { libraryViewModel.items } returns itemsFlow
        every { libraryViewModel.downloadedIds } returns downloadedIdsFlow
        every { libraryViewModel.hiddenIds } returns hiddenIdsFlow
        every { libraryViewModel.filterQuery } returns filterQueryFlow
        every { libraryViewModel.title } returns MutableStateFlow("Library")
        every { libraryViewModel.canGoUp } returns MutableStateFlow(false)
        every { libraryViewModel.isLoading } returns MutableStateFlow(false)
        every { libraryViewModel.error } returns MutableStateFlow(null)
        every { libraryViewModel.sortOption } returns MutableStateFlow(SortOption.TITLE)
        every { libraryViewModel.showDownloadedOnly } returns MutableStateFlow(false)
        every { libraryViewModel.showHiddenItems } returns MutableStateFlow(false)
        // Each row asks for its cover; apiClient.imageApi is an extension with real logic
        mockkStatic(API_CLIENT_EXTENSIONS)
        every { apiClient.imageApi } returns mockk(relaxed = true)
        every { apiClient.baseUrl } returns "https://server"
        every { apiClient.accessToken } returns "token"

        startKoin {
            modules(
                module {
                    single { activityEventHandler }
                    single { apiClient }
                    viewModel { libraryViewModel }
                    viewModel { playerViewModel }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
        unmockkStatic(API_CLIENT_EXTENSIONS)
    }

    private fun audioFile(id: UUID = UUID.randomUUID(), name: String = "Track") =
        LibraryItem.AudioFile(BaseItemDto(id = id, type = BaseItemKind.AUDIO_BOOK, name = name))

    private fun string(resId: Int): String =
        ApplicationProvider.getApplicationContext<Application>().getString(resId)

    private fun launch() = launchFragmentInContainer<LibraryFragment>(themeResId = CommonR.style.AppTheme)

    private fun holdDown(name: String) = composeRule.onNodeWithText(name).performTouchInput { longClick() }

    private fun emitted(): ActivityEvent {
        val eventSlot = slot<ActivityEvent>()
        verify { activityEventHandler.emit(capture(eventSlot)) }
        return eventSlot.captured
    }

    @Test
    fun `a player with nothing loaded in it shows no mini player`() {
        // The player is made as soon as someone signs in, before anything has been played
        (playerViewModel.player as MutableLiveData).value = mockk<ExoPlayer>(relaxed = true)

        launch().use {
            composeRule.onNodeWithTag(VaultTestTags.MINI_PLAYER).assertDoesNotExist()
        }
    }

    @Test
    fun `once a book is loaded, the mini player shows it`() {
        (playerViewModel.player as MutableLiveData).value = mockk<ExoPlayer>(relaxed = true)
        val book = mockk<JellyfinMediaSource>(relaxed = true) {
            every { getName(any()) } returns "Dune"
            every { itemId } returns UUID.randomUUID()
        }

        launch().use {
            (playerViewModel.queueManager.currentMediaSource as MutableLiveData).value = book

            composeRule.onNodeWithTag(VaultTestTags.MINI_PLAYER).assertExists()
            composeRule.onNodeWithText("Dune").assertExists()
        }
    }

    @Test
    fun `tapping an audio file launches the player with a resume queue`() {
        val item = audioFile(name = "Dune")
        itemsFlow.value = listOf(item)
        val queue = LibraryViewModel.AudioQueue(listOf(item.id), 0, listOf("Dune"), null)
        coEvery { libraryViewModel.getAudioQueueWithResume(item.id) } returns queue

        launch().use {
            composeRule.onNodeWithText("Dune").performClick()
            composeRule.waitForIdle()
        }

        (emitted() as ActivityEvent.LaunchNativePlayer).playOptions.ids shouldBe listOf(item.id)
    }

    @Test
    fun `holding a not-yet-downloaded audio file offers to download it`() {
        val item = audioFile(name = "Dune")
        itemsFlow.value = listOf(item)

        launch().use {
            holdDown("Dune")
            composeRule.onNodeWithText(string(R.string.download_action)).performClick()
        }

        (emitted() as ActivityEvent.DownloadTracks).trackIds shouldBe listOf(item.id.toString())
    }

    @Test
    fun `holding an already-downloaded audio file offers to remove it`() {
        val item = audioFile(name = "Dune")
        itemsFlow.value = listOf(item)
        downloadedIdsFlow.value = setOf(item.id.toString())

        launch().use {
            holdDown("Dune")
            composeRule.onNodeWithText(string(R.string.download_action)).assertDoesNotExist()
            composeRule.onNodeWithText(string(R.string.remove_download_action)).performClick()
        }

        (emitted() as ActivityEvent.RemoveTracks).trackIds shouldBe listOf(item.id.toString())
    }

    @Test
    fun `holding an item offers to hide it, and hiding it hides it`() {
        val item = audioFile(name = "Dune")
        itemsFlow.value = listOf(item)

        launch().use {
            holdDown("Dune")
            composeRule.onNodeWithText(string(R.string.hide_action)).performClick()
        }

        verify { libraryViewModel.toggleHidden(item.id) }
    }

    @Test
    fun `holding an already-hidden item offers to unhide it`() {
        val item = audioFile(name = "Dune")
        itemsFlow.value = listOf(item)
        hiddenIdsFlow.value = setOf(item.id.toString())

        launch().use {
            holdDown("Dune")
            composeRule.onNodeWithText(string(R.string.unhide_action)).assertExists()
        }
    }

    @Test
    fun `holding a browse folder opens it instead of offering a menu`() {
        val folder = LibraryItem.BrowseFolder(
            BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.FOLDER, name = "Series", isFolder = true),
        )
        itemsFlow.value = listOf(folder)

        launch().use {
            holdDown("Series")
            composeRule.onNodeWithText(string(R.string.hide_action)).assertDoesNotExist()
        }

        verify { libraryViewModel.openFolder(folder.id, folder.displayName) }
    }

    @Test
    fun `the menu opens settings`() {
        launch().use {
            composeRule.onNode(
                androidx.compose.ui.test.hasContentDescription(string(CommonR.string.vault_more_options)),
            ).performClick()
            composeRule.onNodeWithText(string(R.string.activity_name_settings)).performClick()
        }

        emitted() shouldBe ActivityEvent.OpenSettings
    }

    @Test
    fun `after a rotation the search box still shows the search filtering the list`() {
        filterQueryFlow.value = "potter"

        launch().use { scenario ->
            composeRule.onNodeWithText("potter").assertExists()

            scenario.recreate()

            composeRule.onNodeWithText("potter").assertExists()
        }
    }

    @Test
    fun `when the search is cleared, the box empties too`() {
        filterQueryFlow.value = "potter"

        launch().use {
            // As opening a folder does
            filterQueryFlow.value = ""

            composeRule.onNodeWithText("potter").assertDoesNotExist()
        }
    }

    private companion object {
        const val API_CLIENT_EXTENSIONS = "org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt"
    }
}
