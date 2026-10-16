package org.jellyfin.mobile.library

import android.app.Application
import android.os.Looper
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.R
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.robolectric.mockedPlayerViewModel
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.util.UUID
import org.jellyfin.mobile.common.R as CommonR

/**
 * Tests the audiobook-download long-press menu and tap-to-play, with every dependency mocked through
 * a Koin test module so no network or database is touched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LibraryFragmentTest {

    private val libraryViewModel = mockk<LibraryViewModel>(relaxed = true)
    private val activityEventHandler = mockk<ActivityEventHandler>(relaxed = true)
    private val apiClient = mockk<ApiClient>(relaxed = true)

    private val itemsFlow = MutableStateFlow<List<LibraryItem>>(emptyList())
    private val downloadedIdsFlow = MutableStateFlow<Set<String>>(emptySet())
    private val hiddenIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    @Before
    fun setUp() {
        every { libraryViewModel.items } returns itemsFlow
        every { libraryViewModel.downloadedIds } returns downloadedIdsFlow
        every { libraryViewModel.hiddenIds } returns hiddenIdsFlow
        every { libraryViewModel.title } returns MutableStateFlow("Library")
        every { libraryViewModel.canGoUp } returns MutableStateFlow(false)
        every { libraryViewModel.isLoading } returns MutableStateFlow(false)
        every { libraryViewModel.error } returns MutableStateFlow(null)
        every { libraryViewModel.sortOption } returns MutableStateFlow(SortOption.TITLE)
        every { libraryViewModel.showDownloadedOnly } returns MutableStateFlow(false)
        every { libraryViewModel.showHiddenItems } returns MutableStateFlow(false)
        every { apiClient.baseUrl } returns "https://server"
        every { apiClient.accessToken } returns "token"

        startKoin {
            modules(
                module {
                    single { activityEventHandler }
                    single { apiClient }
                    viewModel { libraryViewModel }
                    viewModel { mockedPlayerViewModel() }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun audioFile(id: UUID = UUID.randomUUID(), name: String = "Track") =
        LibraryItem.AudioFile(BaseItemDto(id = id, type = BaseItemKind.AUDIO_BOOK, name = name))

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun string(resId: Int): String =
        ApplicationProvider.getApplicationContext<Application>().getString(resId)

    @Test
    fun `clicking an audio file launches the player with a resume queue`() {
        val item = audioFile()
        val queue = LibraryViewModel.AudioQueue(listOf(item.id), 0, listOf("Track"), null)
        coEvery { libraryViewModel.getAudioQueueWithResume(item.id) } returns queue

        launchFragmentInContainer<LibraryFragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment -> fragment.onItemClick(item) }
            idle()
        }

        val eventSlot = slot<ActivityEvent>()
        verify { activityEventHandler.emit(capture(eventSlot)) }
        (eventSlot.captured as ActivityEvent.LaunchNativePlayer).playOptions.ids shouldBe listOf(item.id)
    }

    @Test
    fun `long-press on a not-yet-downloaded audio file offers to download it`() {
        val item = audioFile()

        launchFragmentInContainer<LibraryFragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment -> fragment.onItemLongClick(item) }

            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val shadowDialog = shadowOf(dialog)
            shadowDialog.items[0] shouldBe string(R.string.download_action)
        }
    }

    @Test
    fun `long-press on an already-downloaded audio file offers to remove it`() {
        val item = audioFile()
        downloadedIdsFlow.value = setOf(item.id.toString())

        launchFragmentInContainer<LibraryFragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment -> fragment.onItemLongClick(item) }

            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val shadowDialog = shadowOf(dialog)
            shadowDialog.items[0] shouldBe string(R.string.remove_download_action)
        }
    }

    @Test
    fun `tapping the download menu action asks to download the item's tracks`() {
        val item = audioFile(name = "My Chapter")

        launchFragmentInContainer<LibraryFragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment -> fragment.onItemLongClick(item) }

            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            shadowOf(dialog).clickOnItem(0)
        }

        val eventSlot = slot<ActivityEvent>()
        verify { activityEventHandler.emit(capture(eventSlot)) }
        (eventSlot.captured as ActivityEvent.DownloadTracks).trackIds shouldBe listOf(item.id.toString())
    }

    @Test
    fun `tapping remove download asks to remove the item's tracks`() {
        val item = audioFile(name = "My Chapter")
        downloadedIdsFlow.value = setOf(item.id.toString())

        launchFragmentInContainer<LibraryFragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment -> fragment.onItemLongClick(item) }

            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            shadowOf(dialog).clickOnItem(0)
        }

        val eventSlot = slot<ActivityEvent>()
        verify { activityEventHandler.emit(capture(eventSlot)) }
        (eventSlot.captured as ActivityEvent.RemoveTracks).trackIds shouldBe listOf(item.id.toString())
    }

    @Test
    fun `long-press on a not-yet-hidden item offers to hide it as the second menu action`() {
        val item = audioFile()

        launchFragmentInContainer<LibraryFragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment -> fragment.onItemLongClick(item) }

            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val shadowDialog = shadowOf(dialog)
            shadowDialog.items[1] shouldBe string(R.string.hide_action)
        }
    }

    @Test
    fun `long-press on an already-hidden item offers to unhide it`() {
        val item = audioFile()
        hiddenIdsFlow.value = setOf(item.id.toString())

        launchFragmentInContainer<LibraryFragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment -> fragment.onItemLongClick(item) }

            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val shadowDialog = shadowOf(dialog)
            shadowDialog.items[1] shouldBe string(R.string.unhide_action)
        }
    }

    @Test
    fun `tapping the hide menu action toggles the item's hidden state`() {
        val item = audioFile()

        launchFragmentInContainer<LibraryFragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment -> fragment.onItemLongClick(item) }

            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            shadowOf(dialog).clickOnItem(1)
        }

        verify { libraryViewModel.toggleHidden(item.id) }
    }

    @Test
    fun `long-press on a browse folder navigates into it instead of showing a download menu`() {
        val folder = LibraryItem.BrowseFolder(
            BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.FOLDER, name = "Series", isFolder = true),
        )

        launchFragmentInContainer<LibraryFragment>(themeResId = CommonR.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment -> fragment.onItemLongClick(folder) }
        }

        verify { libraryViewModel.openFolder(folder.id, folder.displayName) }
        ShadowAlertDialog.getLatestAlertDialog() shouldBe null
    }
}
