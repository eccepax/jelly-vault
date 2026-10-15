package org.jellyfin.mobile.music.downloads

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.common.downloads.GroupStatus
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class DownloadButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(status: GroupStatus) {
        composeRule.setContent {
            DownloadButton(
                status = status,
                onDownload = { events += "download" },
                onRemove = { events += "remove" },
                onRetry = { events += "retry" },
            )
        }
    }

    @Test
    fun `not downloaded offers to download`() {
        show(GroupStatus.NotDownloaded)

        composeRule.onNodeWithContentDescription("Download").performClick()

        events shouldBe listOf("download")
    }

    @Test
    fun `downloading shows how far it has got, and can be cancelled`() {
        show(GroupStatus.InProgress(done = 3, total = 12))

        composeRule.onNodeWithText("Downloading 3 of 12").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Downloading 3 of 12").performClick()
        composeRule.onNodeWithText("Cancel download").performClick()

        events shouldBe listOf("remove")
    }

    @Test
    fun `waiting for wi-fi says so`() {
        show(GroupStatus.Waiting(done = 0, total = 12))

        composeRule.onNodeWithText("Waiting for Wi-Fi · 0 of 12").assertIsDisplayed()
    }

    @Test
    fun `a failed download says how many failed and retries`() {
        show(GroupStatus.Failed(failed = 2, total = 12))

        composeRule.onNodeWithText("2 songs couldn't download").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Retry download").performClick()

        events shouldBe listOf("retry")
    }

    @Test
    fun `downloaded shows it, and can be removed`() {
        show(GroupStatus.Downloaded)

        composeRule.onNodeWithContentDescription("Downloaded").performClick()
        composeRule.onNodeWithText("Remove download").performClick()

        events shouldBe listOf("remove")
    }

    @Test
    fun `closing the menu removes nothing`() {
        show(GroupStatus.Downloaded)

        composeRule.onNodeWithContentDescription("Downloaded").performClick()
        composeRule.onNodeWithText("Remove download").assertIsDisplayed()

        events shouldBe emptyList()
    }
}
