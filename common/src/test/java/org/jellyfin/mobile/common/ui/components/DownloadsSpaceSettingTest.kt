package org.jellyfin.mobile.common.ui.components

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DownloadsSpaceSettingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(bytes: Long, hasDownloads: Boolean = bytes > 0L) {
        composeRule.setContent { DownloadsSpaceSetting(bytes, hasDownloads, onRemoveAll = { events += "removeAll" }) }
    }

    @Test
    fun `shows the space used, and removing all asks first`() {
        show(bytes = 1_500_000_000L)

        composeRule.onNodeWithText("Downloads use 1.5 GB").assertIsDisplayed()
        composeRule.onNodeWithText("Remove all downloads").performClick()
        events shouldBe emptyList()
        composeRule.onNodeWithText("Yes, remove all").performClick()

        events shouldBe listOf("removeAll")
    }

    @Test
    fun `changing your mind removes nothing`() {
        show(bytes = 1_500_000_000L)

        composeRule.onNodeWithText("Remove all downloads").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        events shouldBe emptyList()
        composeRule.onNodeWithText("Remove all downloads").assertIsDisplayed()
    }

    @Test
    fun `with nothing downloaded there is nothing to remove`() {
        show(bytes = 0L)

        composeRule.onNodeWithText("Nothing downloaded").assertIsDisplayed()
        composeRule.onNodeWithText("Remove all downloads").assertDoesNotExist()
    }

    @Test
    fun `downloads waiting for wi-fi can be removed though they take no space yet`() {
        show(bytes = 0L, hasDownloads = true)

        composeRule.onNodeWithText("Remove all downloads").performClick()
        composeRule.onNodeWithText("Yes, remove all").performClick()

        events shouldBe listOf("removeAll")
    }

    @Test
    fun `a question left open when everything goes isn't asked again when something downloads`() {
        var hasDownloads by mutableStateOf(true)
        composeRule.setContent { DownloadsSpaceSetting(1_000L, hasDownloads, onRemoveAll = { events += "removeAll" }) }
        composeRule.onNodeWithText("Remove all downloads").performClick()

        hasDownloads = false
        composeRule.onNodeWithText("Nothing downloaded").assertIsDisplayed()
        hasDownloads = true

        composeRule.onNodeWithText("Yes, remove all").assertDoesNotExist()
        composeRule.onNodeWithText("Remove all downloads").assertIsDisplayed()
    }
}
