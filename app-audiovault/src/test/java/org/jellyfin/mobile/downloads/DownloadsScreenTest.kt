package org.jellyfin.mobile.downloads

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.jellyfin.mobile.common.R as CommonR

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DownloadsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun download(id: String) = mockk<DownloadEntity> { every { itemId } returns id }

    private val dune = DownloadRow(
        download("dune"),
        title = "Dune",
        subtitle = "Frank Herbert · 312 MB",
        coverUrl = null,
    )
    private val kindred = DownloadRow(download("kindred"), title = "Kindred", subtitle = null, coverUrl = null)
    private val events = mutableListOf<String>()

    private fun show(rows: List<DownloadRow>) {
        composeRule.setContent {
            AppTheme(VaultBrand.AudioVault) {
                DownloadsScreen(
                    rows = rows,
                    actions = DownloadsActions(
                        onBack = { events += "back" },
                        onPlay = { events += "play ${title(it)}" },
                        onRemove = { events += "remove ${title(it)}" },
                    ),
                )
            }
        }
    }

    private fun title(download: DownloadEntity) = listOf(dune, kindred).first { it.download === download }.title

    private fun string(resId: Int) = ApplicationProvider.getApplicationContext<Application>().getString(resId)

    @Test
    fun `each download shows its title and details`() {
        show(listOf(dune, kindred))

        composeRule.onNodeWithText("Dune").assertExists()
        composeRule.onNodeWithText("Frank Herbert · 312 MB").assertExists()
        composeRule.onNodeWithText("Kindred").assertExists()
    }

    @Test
    fun `tapping a download plays it`() {
        show(listOf(dune, kindred))

        composeRule.onNodeWithText("Kindred").performClick()

        events shouldBe listOf("play Kindred")
    }

    @Test
    fun `holding a download asks to remove it`() {
        show(listOf(dune, kindred))

        composeRule.onNodeWithText("Dune").performTouchInput { longClick() }

        events shouldBe listOf("remove Dune")
    }

    @Test
    fun `with nothing downloaded, it says so`() {
        show(emptyList())

        composeRule.onNodeWithText(string(R.string.downloads_empty)).assertExists()
    }

    @Test
    fun `back leaves downloads`() {
        show(emptyList())

        composeRule.onNodeWithContentDescription(string(CommonR.string.vault_back)).performClick()

        events shouldBe listOf("back")
    }
}
