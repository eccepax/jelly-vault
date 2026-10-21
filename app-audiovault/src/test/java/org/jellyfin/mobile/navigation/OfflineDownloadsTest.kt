package org.jellyfin.mobile.navigation

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

/** Downloads can be listened to before signing in, opened from the connect screen. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class OfflineDownloadsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val content: @Composable () -> Unit = {
        WithOfflineDownloads(
            app = { openDownloads -> Button(onClick = openDownloads) { Text("Connect: view downloads") } },
            downloads = { onBack -> Button(onClick = onBack) { Text("Downloads page") } },
        )
    }

    private fun systemBack() {
        composeRule.waitForIdle()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    }

    @Test
    fun `the app shows until the downloads are asked for`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Connect: view downloads").performClick()

        composeRule.onNodeWithText("Downloads page").assertIsDisplayed()
    }

    @Test
    fun `back from the downloads returns to the app, by their button or the system's`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Connect: view downloads").performClick()
        composeRule.onNodeWithText("Downloads page").performClick()
        composeRule.onNodeWithText("Connect: view downloads").assertIsDisplayed()

        composeRule.onNodeWithText("Connect: view downloads").performClick()
        systemBack()
        composeRule.onNodeWithText("Connect: view downloads").assertIsDisplayed()
    }

    @Test
    fun `the downloads stay open across a rotation`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent(content)
        composeRule.onNodeWithText("Connect: view downloads").performClick()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Downloads page").assertIsDisplayed()
    }
}
