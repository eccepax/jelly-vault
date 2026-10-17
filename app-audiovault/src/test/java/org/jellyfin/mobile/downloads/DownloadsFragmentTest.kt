package org.jellyfin.mobile.downloads

import android.app.Application
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.fragment.app.testing.launchFragmentInContainer
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
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

/** Tests playing and removing an already-downloaded item. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DownloadsFragmentTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val downloadsViewModel = mockk<DownloadsViewModel>(relaxed = true)
    private val activityEventHandler = mockk<ActivityEventHandler>(relaxed = true)
    private val apiClient = mockk<ApiClient>(relaxed = true)
    private val downloadsFlow = MutableStateFlow<List<DownloadEntity>>(emptyList())

    @Before
    fun setUp() {
        every { downloadsViewModel.downloads } returns downloadsFlow
        // The list shows each download's cover; apiClient.imageApi is an extension with real logic
        mockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
        every { apiClient.imageApi } returns mockk(relaxed = true)

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
        unmockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
    }

    private fun downloadEntity(
        itemId: UUID = UUID.randomUUID(),
        mediaSourceId: String = "source-1",
        item: BaseItemDto? = null,
        name: String = "Dune",
    ): DownloadEntity {
        val mediaSource = mockk<LocalJellyfinMediaSource>(relaxed = true)
        every { mediaSource.itemId } returns (item?.id ?: itemId)
        every { mediaSource.id } returns mediaSourceId
        every { mediaSource.item } returns item
        every { mediaSource.getName(any()) } returns name
        return DownloadEntity(itemId = itemId.toString(), mediaSource = mediaSource)
    }

    @Test
    fun `clicking a downloaded track plays its downloaded book from that track`() {
        val chapter1 = downloadEntity(item = chapter(1), name = "Chapter 1")
        val chapter2 = downloadEntity(item = chapter(2), name = "Chapter 2")
        downloadsFlow.value = listOf(chapter2, chapter1)

        launchFragmentInContainer<DownloadsFragment>(themeResId = CommonR.style.AppTheme).use {
            composeRule.onNodeWithText("Chapter 2").performClick()
        }

        val eventSlot = slot<ActivityEvent>()
        verify { activityEventHandler.emit(capture(eventSlot)) }
        val playOptions = (eventSlot.captured as ActivityEvent.LaunchNativePlayer).playOptions
        playOptions.ids shouldBe listOf(chapter1.mediaSource.itemId, chapter2.mediaSource.itemId)
        playOptions.startIndex shouldBe 1
        playOptions.playFromDownloads shouldBe true
    }

    private fun chapter(index: Int) =
        BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.AUDIO,
            indexNumber = index,
            path = "/books/Dune/$index.mp3",
            album = "Dune",
        )

    @Test
    fun `holding a downloaded item requests its removal`() {
        val download = downloadEntity(name = "Dune")
        downloadsFlow.value = listOf(download)

        launchFragmentInContainer<DownloadsFragment>(themeResId = CommonR.style.AppTheme).use {
            composeRule.onNodeWithText("Dune").performTouchInput { longClick() }
        }

        val eventSlot = slot<ActivityEvent>()
        verify { activityEventHandler.emit(capture(eventSlot)) }
        // ActivityEvent.RemoveDownload isn't a data class, so compare by the wrapped media source
        // reference rather than by event equality.
        (eventSlot.captured as ActivityEvent.RemoveDownload).download shouldBe download.mediaSource
    }
}
