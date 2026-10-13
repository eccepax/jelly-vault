package org.jellyfin.mobile.music.nowplaying

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.playback.SleepTimerState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class SleepTimerButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()
    private val minute = 60_000L

    private fun show(state: SleepTimerState) {
        val ui = SleepTimerUi(
            state = state,
            onStart = { events += "start ${it / minute}m" },
            onEndOfSong = { events += "endOfSong" },
            onExtend = { events += "extend" },
            onCancel = { events += "cancel" },
        )
        composeRule.setContent {
            Column {
                SleepTimerButton(ui)
                SleepTimerLabel(ui.state)
            }
        }
    }

    private fun openMenu() = composeRule.onNodeWithContentDescription("Sleep timer").performClick()

    @Test
    fun `off, it offers durations and the end of the song`() {
        show(SleepTimerState.Off)
        openMenu()

        composeRule.onNodeWithText("15 minutes").assertIsDisplayed()
        composeRule.onNodeWithText("1 hour").assertIsDisplayed()
        composeRule.onNodeWithText("30 minutes").performClick()
        openMenu()
        composeRule.onNodeWithText("End of this song").performClick()

        events shouldBe listOf("start 30m", "endOfSong")
        composeRule.onNodeWithText("Turn off").assertDoesNotExist()
    }

    @Test
    fun `counting down, it shows the time left and can be extended or turned off`() {
        show(SleepTimerState.Counting(remainingMs = 29 * minute + 59_500L))

        composeRule.onNodeWithText("Sleeps in 30:00").assertIsDisplayed()
        openMenu()
        composeRule.onNodeWithText("+15 minutes").performClick()
        openMenu()
        composeRule.onNodeWithText("Turn off").performClick()

        events shouldBe listOf("extend", "cancel")
    }

    @Test
    fun `an hour or more shows hours`() {
        show(SleepTimerState.Counting(remainingMs = 75 * minute))

        composeRule.onNodeWithText("Sleeps in 1:15:00").assertIsDisplayed()
    }

    @Test
    fun `end of song says so, and can be turned off`() {
        show(SleepTimerState.EndOfSong)

        composeRule.onNodeWithText("Sleeps after this song").assertIsDisplayed()
        openMenu()
        composeRule.onNodeWithText("+15 minutes").assertDoesNotExist()
        composeRule.onNodeWithText("Turn off").performClick()

        events shouldBe listOf("cancel")
    }

    @Test
    fun `off, there is no label`() {
        show(SleepTimerState.Off)

        composeRule.onNodeWithText("Sleeps", substring = true).assertDoesNotExist()
    }
}
