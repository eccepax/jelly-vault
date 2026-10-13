package org.jellyfin.mobile.music.nowplaying

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.domain.track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class QueueScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val state = QueueUiState(
        nowPlaying = QueueRow(index = 1, track = track("b").copy(title = "London May")),
        upNext = listOf(
            QueueRow(index = 2, track = track("c").copy(title = "Boise, Idaho")),
            QueueRow(index = 3, track = track("d").copy(title = "New Water")),
            QueueRow(index = 4, track = track("e").copy(title = "Downstream")),
        ),
    )
    private val events = mutableListOf<String>()

    private fun show(state: QueueUiState) {
        composeRule.setContent {
            QueueScreen(
                state = state,
                onBack = { events += "back" },
                onSkipTo = { events += "skipTo $it" },
                onRemove = { events += "remove $it" },
                onMove = { from, to -> events += "move $from to $to" },
            )
        }
    }

    private fun SemanticsNodeInteraction.customActions(): List<String> =
        fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }

    private fun SemanticsNodeInteraction.runCustomAction(label: String) {
        val action = fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == label }
        composeRule.runOnUiThread { action.action() }
    }

    @Test
    fun `shows now playing and up next`() {
        show(state)

        composeRule.onNodeWithText("Now playing").assertIsDisplayed()
        composeRule.onNodeWithText("London May").assertIsDisplayed()
        composeRule.onNodeWithText("Up next").assertIsDisplayed()
        composeRule.onNodeWithText("Downstream").assertIsDisplayed()
    }

    @Test
    fun `tapping an upcoming track jumps to it`() {
        show(state)

        composeRule.onNodeWithText("New Water").performClick()

        events shouldBe listOf("skipTo 3")
    }

    @Test
    fun `an upcoming track can be removed`() {
        show(state)

        composeRule.onNodeWithContentDescription("Remove Boise, Idaho").performClick()

        events shouldBe listOf("remove 2")
    }

    @Test
    fun `upcoming tracks can be moved up and down by accessibility actions`() {
        show(state)

        composeRule.onNodeWithText("New Water").runCustomAction("Move up")
        composeRule.onNodeWithText("New Water").runCustomAction("Move down")

        events shouldBe listOf("move 3 to 2", "move 3 to 4")
    }

    @Test
    fun `the first upcoming track can't move above now playing, nor the last below the end`() {
        show(state)

        composeRule.onNodeWithText("Boise, Idaho").customActions() shouldNotContain "Move up"
        composeRule.onNodeWithText("Boise, Idaho").customActions() shouldContain "Move down"
        composeRule.onNodeWithText("Downstream").customActions() shouldNotContain "Move down"
    }

    @Test
    fun `dragging a handle down two rows moves the track there`() {
        show(state)
        // The row merges its children into one node; the handle icon itself is in the unmerged tree.
        val handles = composeRule.onAllNodesWithContentDescription("Drag to reorder", useUnmergedTree = true)
        val rowHeight = handles[1].getBoundsInRoot().top - handles[0].getBoundsInRoot().top

        handles[0].performTouchInput {
            down(center)
            repeat(20) { step -> moveTo(center.copy(y = center.y + rowHeight.toPx() * 2.2f * (step + 1) / 20)) }
            up()
        }
        composeRule.waitForIdle()

        events shouldBe listOf("move 2 to 4")
    }

    @Test
    fun `an empty up next says so`() {
        show(state.copy(upNext = emptyList()))

        composeRule.onNodeWithText("Nothing up next").assertIsDisplayed()
    }

    @Test
    fun `back goes back`() {
        show(state)

        composeRule.onNodeWithContentDescription("Back").performClick()

        events shouldBe listOf("back")
    }
}
