package org.jellyfin.mobile.common.ui.screens.connect

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** On a TV the remote moves focus between buttons, so the focused one has to stand out. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class StyledTextButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun outlined(shown: Boolean) = SemanticsMatcher.expectValue(FocusOutlined, shown)

    private fun showTwoButtonsOnATv() {
        composeRule.setContent {
            // A TV has no touch screen: the remote keeps it in keyboard input mode.
            val inputMode = LocalInputModeManager.current
            var ready by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                inputMode.requestInputMode(InputMode.Keyboard)
                snapshotFlow { inputMode.inputMode }.first { it == InputMode.Keyboard }
                ready = true
            }
            if (ready) {
                Column {
                    StyledTextButton(text = "Connect", onClick = {})
                    StyledTextButton(text = "Choose a server", onClick = {})
                }
            }
        }
    }

    @Test
    fun `the focused button is outlined, and the others are not`() {
        showTwoButtonsOnATv()

        composeRule.onNodeWithText("Connect").requestFocus()

        composeRule.onNodeWithText("Connect").assert(outlined(true))
        composeRule.onNodeWithText("Choose a server").assert(outlined(false))
    }

    @Test
    fun `moving focus moves the outline`() {
        showTwoButtonsOnATv()
        composeRule.onNodeWithText("Connect").requestFocus()

        composeRule.onNodeWithText("Choose a server").requestFocus()

        composeRule.onNodeWithText("Connect").assert(outlined(false))
        composeRule.onNodeWithText("Choose a server").assert(outlined(true))
    }
}
