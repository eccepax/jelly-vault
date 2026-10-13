package org.jellyfin.mobile.music.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
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
class MainTabsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun Counter(label: String) {
        var taps by rememberSaveable { mutableIntStateOf(0) }
        Button(onClick = { taps++ }) { Text("$label taps: $taps") }
    }

    private val content: @Composable () -> Unit = {
        var tab by rememberSaveable { mutableIntStateOf(MainTab.START.ordinal) }
        val selected = MainTab.entries[tab]
        Column(Modifier.fillMaxSize()) {
            MainTabs(
                selected = selected,
                onSelect = { tab = it.ordinal },
                home = { Counter("Home") },
                library = { Counter("Library") },
                search = { Counter("Search") },
                modifier = Modifier.weight(1f),
            )
            MainBottomBar(selected = selected, onSelect = { tab = it.ordinal })
        }
    }

    private fun systemBack() {
        composeRule.waitForIdle()
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    }

    @Test
    fun `opens on the home tab`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Home taps: 0").assertIsDisplayed()
        composeRule.onNodeWithText("Home").assertIsSelected()
    }

    @Test
    fun `the bar switches to the library`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Library").performClick()

        composeRule.onNodeWithText("Library taps: 0").assertIsDisplayed()
        composeRule.onNodeWithText("Library").assertIsSelected()
    }

    @Test
    fun `the bar switches to search`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Search").performClick()

        composeRule.onNodeWithText("Search taps: 0").assertIsDisplayed()
        composeRule.onNodeWithText("Home taps: 0").assertDoesNotExist()
        composeRule.onNodeWithText("Search").assertIsSelected()
    }

    @Test
    fun `each tab keeps its state while the other is shown`() {
        composeRule.setContent(content)
        composeRule.onNodeWithText("Library").performClick()
        composeRule.onNodeWithText("Library taps: 0").performClick()

        composeRule.onNodeWithText("Search").performClick()
        composeRule.onNodeWithText("Search taps: 0").performClick()
        composeRule.onNodeWithText("Library").performClick()

        composeRule.onNodeWithText("Library taps: 1").assertIsDisplayed()
        composeRule.onNodeWithText("Search").performClick()
        composeRule.onNodeWithText("Search taps: 1").assertIsDisplayed()
    }

    @Test
    fun `back from search returns home`() {
        composeRule.setContent(content)
        composeRule.onNodeWithText("Search").performClick()

        systemBack()

        composeRule.onNodeWithText("Home taps: 0").assertIsDisplayed()
    }

    @Test
    fun `back from the library returns home`() {
        composeRule.setContent(content)
        composeRule.onNodeWithText("Library").performClick()

        systemBack()

        composeRule.onNodeWithText("Home taps: 0").assertIsDisplayed()
    }

    @Test
    fun `back on the home tab is left to the system`() {
        composeRule.setContent(content)
        composeRule.waitForIdle()

        composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks() shouldBe false
    }

    @Test
    fun `the selected tab survives recreation`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent(content)
        composeRule.onNodeWithText("Search").performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Search taps: 0").assertIsDisplayed()
    }
}
