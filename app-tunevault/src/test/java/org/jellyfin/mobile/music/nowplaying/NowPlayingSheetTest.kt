package org.jellyfin.mobile.music.nowplaying

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NowPlayingSheetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val content: @Composable () -> Unit = {
        NowPlayingSheet(
            nowPlaying = { onOpenQueue -> Button(onClick = onOpenQueue) { Text("Now playing; open queue") } },
            queue = { onBack -> Button(onClick = onBack) { Text("Queue; back") } },
        )
    }

    @Test
    fun `starts on now playing`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Now playing; open queue").assertIsDisplayed()
    }

    @Test
    fun `opens the queue, and its back returns to now playing`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Now playing; open queue").performClick()
        composeRule.onNodeWithText("Queue; back").performClick()

        composeRule.onNodeWithText("Now playing; open queue").assertIsDisplayed()
    }

    @Test
    fun `system back from the queue returns to now playing`() {
        composeRule.setContent(content)
        composeRule.onNodeWithText("Now playing; open queue").performClick()
        composeRule.waitForIdle()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithText("Now playing; open queue").assertIsDisplayed()
    }

    @Test
    fun `the open queue survives recreation`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent(content)
        composeRule.onNodeWithText("Now playing; open queue").performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Queue; back").assertIsDisplayed()
    }
}
