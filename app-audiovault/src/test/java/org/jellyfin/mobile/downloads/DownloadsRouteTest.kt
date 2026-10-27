package org.jellyfin.mobile.downloads

import android.app.Application
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import org.jellyfin.mobile.common.R as CommonR

/** Playing and removing what's downloaded. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DownloadsRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val downloads = MutableStateFlow<List<DownloadEntity>>(emptyList())
    private val removed = mutableListOf<String>()
    private val played = mutableListOf<PlayOptions>()
    private var backs = 0

    private fun show() {
        val viewModel = DownloadsViewModel(
            downloads,
            downloadTracks = {},
            removeTracks = { removed += it },
            repairDownloads = {},
        )
        composeRule.setContent {
            AppTheme(VaultBrand.AudioVault) {
                DownloadsRoute(
                    onBack = { backs++ },
                    onPlay = { played += it },
                    viewModel = viewModel,
                    coverUrl = { _, _ -> null },
                )
            }
        }
    }

    private fun downloadEntity(item: BaseItemDto? = null, name: String = "Dune"): DownloadEntity {
        val itemId = item?.id ?: UUID.randomUUID()
        val mediaSource = mockk<LocalJellyfinMediaSource>(relaxed = true)
        every { mediaSource.itemId } returns itemId
        // As the server gives it: the track's id, without its dashes
        every { mediaSource.id } returns itemId.toString().replace("-", "")
        every { mediaSource.item } returns item
        every { mediaSource.getName(any()) } returns name
        return DownloadEntity(mediaSource)
    }

    private fun chapter(index: Int) = BaseItemDto(
        id = UUID.randomUUID(),
        type = BaseItemKind.AUDIO,
        indexNumber = index,
        path = "/books/Dune/$index.mp3",
        album = "Dune",
    )

    @Test
    fun `clicking a downloaded track plays its downloaded book from that track`() {
        val chapter1 = downloadEntity(item = chapter(1), name = "Chapter 1")
        val chapter2 = downloadEntity(item = chapter(2), name = "Chapter 2")
        downloads.value = listOf(chapter2, chapter1)
        show()

        composeRule.onNodeWithText("Chapter 2").performClick()

        val playOptions = played.single()
        playOptions.ids shouldBe listOf(chapter1.mediaSource.itemId, chapter2.mediaSource.itemId)
        playOptions.startIndex shouldBe 1
        playOptions.playFromDownloads shouldBe true
    }

    @Test
    fun `holding a downloaded item asks first, and yes removes it`() {
        val download = downloadEntity(name = "Dune")
        downloads.value = listOf(download)
        show()

        composeRule.onNodeWithText("Dune").performTouchInput { longClick() }
        composeRule.onNodeWithText(context.getString(R.string.confirm_deletion_desc, "Dune")).assertExists()
        removed.shouldBeEmpty()
        composeRule.onNodeWithText(context.getString(R.string.yes)).performClick()
        composeRule.waitForIdle()

        removed shouldBe listOf(download.mediaSource.itemId.toString())
        composeRule.onNodeWithText(context.getString(R.string.confirm_deletion_desc, "Dune")).assertDoesNotExist()
    }

    @Test
    fun `asking to remove survives a rotation`() {
        val download = downloadEntity(name = "Dune")
        downloads.value = listOf(download)
        val restoration = StateRestorationTester(composeRule)
        val viewModel = DownloadsViewModel(
            downloads,
            downloadTracks = {},
            removeTracks = { removed += it },
            repairDownloads = {},
        )
        restoration.setContent {
            AppTheme(VaultBrand.AudioVault) {
                DownloadsRoute(onBack = {}, onPlay = {}, viewModel = viewModel, coverUrl = { _, _ -> null })
            }
        }
        composeRule.onNodeWithText("Dune").performTouchInput { longClick() }

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText(context.getString(R.string.confirm_deletion_desc, "Dune")).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.yes)).performClick()
        composeRule.waitForIdle()
        removed shouldBe listOf(download.mediaSource.itemId.toString())
    }

    @Test
    fun `cancelling keeps the download`() {
        downloads.value = listOf(downloadEntity(name = "Dune"))
        show()

        composeRule.onNodeWithText("Dune").performTouchInput { longClick() }
        composeRule.onNodeWithText(context.getString(CommonR.string.vault_cancel)).performClick()
        composeRule.waitForIdle()

        removed.shouldBeEmpty()
        composeRule.onNodeWithText(context.getString(R.string.confirm_deletion_desc, "Dune")).assertDoesNotExist()
    }

    @Test
    fun `back goes back`() {
        show()

        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.vault_back)).performClick()

        backs shouldBe 1
    }
}
