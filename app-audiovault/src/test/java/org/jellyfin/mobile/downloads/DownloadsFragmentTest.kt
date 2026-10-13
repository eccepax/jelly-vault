package org.jellyfin.mobile.downloads

import android.app.Application
import androidx.fragment.app.testing.launchFragmentInContainer
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.R
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.sdk.api.client.ApiClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** Tests playing and removing an already-downloaded item. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DownloadsFragmentTest {

    private val downloadsViewModel = mockk<DownloadsViewModel>(relaxed = true)
    private val activityEventHandler = mockk<ActivityEventHandler>(relaxed = true)
    private val apiClient = mockk<ApiClient>(relaxed = true)
    private val downloadsFlow = MutableStateFlow<List<DownloadEntity>>(emptyList())

    @Before
    fun setUp() {
        every { downloadsViewModel.downloads } returns downloadsFlow

        startKoin {
            modules(
                module {
                    single { activityEventHandler }
                    single { apiClient }
                    viewModel { downloadsViewModel }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private fun downloadEntity(itemId: UUID = UUID.randomUUID(), mediaSourceId: String = "source-1"): DownloadEntity {
        val mediaSource = mockk<LocalJellyfinMediaSource>(relaxed = true)
        every { mediaSource.itemId } returns itemId
        every { mediaSource.id } returns mediaSourceId
        return DownloadEntity(itemId = itemId.toString(), mediaSource = mediaSource)
    }

    @Test
    fun `clicking a downloaded item launches the player from downloads`() {
        val download = downloadEntity()

        launchFragmentInContainer<DownloadsFragment>(themeResId = R.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment -> fragment.onDownloadItemClick(download) }
        }

        val eventSlot = slot<ActivityEvent>()
        verify { activityEventHandler.emit(capture(eventSlot)) }
        val playOptions = (eventSlot.captured as ActivityEvent.LaunchNativePlayer).playOptions
        playOptions.ids shouldBe listOf(download.mediaSource.itemId)
        playOptions.mediaSourceId shouldBe download.mediaSource.id
        playOptions.playFromDownloads shouldBe true
    }

    @Test
    fun `holding a downloaded item requests its removal`() {
        val download = downloadEntity()

        launchFragmentInContainer<DownloadsFragment>(themeResId = R.style.AppTheme).use { scenario ->
            scenario.onFragment { fragment -> fragment.onDownloadItemHold(download) }
        }

        val eventSlot = slot<ActivityEvent>()
        verify { activityEventHandler.emit(capture(eventSlot)) }
        // ActivityEvent.RemoveDownload isn't a data class, so compare by the wrapped media source
        // reference rather than by event equality.
        (eventSlot.captured as ActivityEvent.RemoveDownload).download shouldBe download.mediaSource
    }
}
