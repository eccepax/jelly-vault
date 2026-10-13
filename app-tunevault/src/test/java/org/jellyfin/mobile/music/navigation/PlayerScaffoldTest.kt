package org.jellyfin.mobile.music.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class PlayerScaffoldTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var hasTrack by mutableStateOf(true)

    private val content: @Composable () -> Unit = {
        PlayerScaffold(
            hasTrack = hasTrack,
            library = { Text("Library") },
            miniPlayer = { onExpand -> Button(onClick = onExpand) { Text("Mini player") } },
            nowPlaying = { onCollapse -> Button(onClick = onCollapse) { Text("Now playing") } },
        )
    }

    @Test
    fun `no mini player until something is playing`() {
        hasTrack = false
        composeRule.setContent(content)

        composeRule.onNodeWithText("Library").assertIsDisplayed()
        composeRule.onNodeWithText("Mini player").assertDoesNotExist()
    }

    @Test
    fun `the mini player sits under the library while playing`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Library").assertIsDisplayed()
        composeRule.onNodeWithText("Mini player").assertIsDisplayed()
    }

    @Test
    fun `the mini player sits just above the bottom bar`() {
        composeRule.setContent {
            PlayerScaffold(
                hasTrack = true,
                library = { Text("Library") },
                miniPlayer = { onExpand -> Button(onClick = onExpand) { Text("Mini player") } },
                nowPlaying = { onCollapse -> Button(onClick = onCollapse) { Text("Now playing") } },
                bottomBar = { Text("Bottom bar") },
            )
        }

        val mini = composeRule.onNodeWithText("Mini player").getUnclippedBoundsInRoot()
        val bar = composeRule.onNodeWithText("Bottom bar").getUnclippedBoundsInRoot()
        (mini.bottom <= bar.top) shouldBe true
    }

    @Test
    fun `the bottom bar shows even with nothing playing`() {
        composeRule.setContent {
            PlayerScaffold(
                hasTrack = false,
                library = { Text("Library") },
                miniPlayer = { Text("Mini player") },
                nowPlaying = { Text("Now playing") },
                bottomBar = { Text("Bottom bar") },
            )
        }

        composeRule.onNodeWithText("Bottom bar").assertIsDisplayed()
    }

    @Test
    fun `tapping the mini player opens now playing, and collapse closes it`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Mini player").performClick()
        composeRule.onNodeWithText("Now playing").assertIsDisplayed()

        composeRule.onNodeWithText("Now playing").performClick()
        composeRule.onNodeWithText("Now playing").assertDoesNotExist()
        composeRule.onNodeWithText("Mini player").assertIsDisplayed()
    }

    @Test
    fun `content covered by now playing is hidden from accessibility services`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Mini player").performClick()

        composeRule.onNodeWithText("Now playing").assertIsDisplayed()
        composeRule.onNodeWithText("Library").assertDoesNotExist()
        composeRule.onNodeWithText("Mini player").assertDoesNotExist()
    }

    @Test
    fun `system back collapses now playing`() {
        composeRule.setContent(content)
        composeRule.onNodeWithText("Mini player").performClick()
        composeRule.waitForIdle() // see LibraryNavigationTest: BackHandler enables on the next recomposition

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithText("Now playing").assertDoesNotExist()
        composeRule.onNodeWithText("Mini player").assertIsDisplayed()
    }

    @Test
    fun `system back collapses now playing before the library's own back handling`() {
        // e.g. an album open underneath: back must close Now Playing, not the hidden album.
        var libraryBacks = 0
        composeRule.setContent {
            PlayerScaffold(
                hasTrack = true,
                library = {
                    BackHandler { libraryBacks++ }
                    Text("Library")
                },
                miniPlayer = { onExpand -> Button(onClick = onExpand) { Text("Mini player") } },
                nowPlaying = { onCollapse -> Button(onClick = onCollapse) { Text("Now playing") } },
            )
        }
        composeRule.onNodeWithText("Mini player").performClick()
        composeRule.waitForIdle()

        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeRule.onNodeWithText("Now playing").assertDoesNotExist()
        libraryBacks shouldBe 0
    }

    @Test
    fun `now playing stays open across activity recreation`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent(content)
        composeRule.onNodeWithText("Mini player").performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Now playing").assertIsDisplayed()
    }

    @Test
    fun `now playing closes when the queue empties`() {
        composeRule.setContent(content)
        composeRule.onNodeWithText("Mini player").performClick()

        hasTrack = false

        composeRule.onNodeWithText("Now playing").assertDoesNotExist()
        composeRule.onNodeWithText("Library").assertIsDisplayed()
    }
}
