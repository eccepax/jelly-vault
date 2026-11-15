package org.jellyfin.mobile.common.ui.components

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.common.playback.SleepTimerState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sleep timer's length of the listener's own, typed in minutes. Apart from SleepTimerButtonTest:
 * under its screen size Robolectric never settles once a text field dialog opens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SleepTimerCustomLengthTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()
    private val minute = 60_000L

    private fun show(state: SleepTimerState) {
        val ui = SleepTimerUi(state = state, onStart = { events += "start ${it / minute}m" })
        composeRule.setContent { SleepTimerButton(ui, endOfSegment = "End of this chapter") }
    }

    private fun openMenu() = composeRule.onNodeWithContentDescription("Sleep timer").performClick()

    @Test
    fun `off, a length of its own can be typed in, in minutes`() {
        show(SleepTimerState.Off)
        openMenu()

        composeRule.onNodeWithText("Other length…").performClick()
        composeRule.onNodeWithText("Minutes").performTextReplacement("20")
        composeRule.onNodeWithText("Start").performClick()

        events shouldBe listOf("start 20m")
    }

    @Test
    fun `a length that isn't a number of minutes starts nothing`() {
        show(SleepTimerState.Off)
        listOf("0", "abc", "").forEach { typed ->
            openMenu()
            composeRule.onNodeWithText("Other length…").performClick()
            if (typed.isNotEmpty()) composeRule.onNodeWithText("Minutes").performTextReplacement(typed)
            composeRule.onNodeWithText("Start").performClick()
        }

        events shouldBe emptyList()
    }
}
