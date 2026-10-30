package org.jellyfin.mobile.cast

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlayingOnLabelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a stop-casting question left open when the cast ends isn't asked again on the next cast`() {
        var device by mutableStateOf<String?>("Living Room TV")
        composeRule.setContent { PlayingOnLabel(castDevice = device, onStopCasting = {}) }
        composeRule.onNodeWithText("Playing on Living Room TV").performClick()
        composeRule.onNodeWithText("Stop casting").assertExists()

        device = null
        composeRule.waitForIdle()
        device = "Living Room TV"

        composeRule.onNodeWithText("Playing on Living Room TV").assertExists()
        composeRule.onNodeWithText("Stop casting").assertDoesNotExist()
    }
}
