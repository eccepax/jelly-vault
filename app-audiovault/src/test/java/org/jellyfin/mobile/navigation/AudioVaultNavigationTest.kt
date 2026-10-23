package org.jellyfin.mobile.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.common.ui.RootScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AudioVaultNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Where signing in has got to, as the connect screen and sign-in would have it. */
    private var root by mutableStateOf(RootScreen.SignedIn)

    /** Stand-in screens; home has saveable state (like a scroll position) to keep while covered. */
    private val content: @Composable () -> Unit = {
        AudioVaultNavigation(
            rootScreen = root,
            home = { navigator ->
                var taps by rememberSaveable { mutableIntStateOf(0) }
                Column {
                    Button(onClick = { taps++ }) { Text("Home taps: $taps") }
                    Button(onClick = navigator.openPlayer) { Text("Open player") }
                    Button(onClick = navigator.openDownloads) { Text("Open downloads") }
                    Button(onClick = navigator.openSettings) { Text("Open settings") }
                }
            },
            player = { onBack ->
                Column {
                    Button(onClick = onBack) { Text("Player page") }
                    // As the back arrow tapped just as the book stops: the player leaves twice at once
                    Button(onClick = {
                        onBack()
                        onBack()
                    }) { Text("Leave twice") }
                }
            },
            downloads = { onBack, navigator ->
                Column {
                    Button(onClick = onBack) { Text("Downloads page") }
                    Button(onClick = navigator.openPlayer) { Text("Play a download") }
                }
            },
            settings = { onBack -> Button(onClick = onBack) { Text("Settings page") } },
        )
    }

    private fun tap(text: String) = composeRule.onNodeWithText(text).performClick()

    private fun systemBack() {
        composeRule.waitForIdle() // BackHandler enables on the recomposition after a navigation
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    }

    @Test
    fun `it starts at home`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Home taps: 0").assertIsDisplayed()
    }

    @Test
    fun `each page opens over home, and back returns to it`() {
        composeRule.setContent(content)

        for ((open, page) in listOf(
            "Open player" to "Player page",
            "Open downloads" to "Downloads page",
            "Open settings" to "Settings page",
        )) {
            tap(open)
            composeRule.onNodeWithText(page).assertIsDisplayed()
            systemBack()
            composeRule.onNodeWithText("Home taps: 0").assertIsDisplayed()
        }
    }

    @Test
    fun `a page's own back goes back too`() {
        composeRule.setContent(content)

        tap("Open settings")
        tap("Settings page")

        composeRule.onNodeWithText("Home taps: 0").assertIsDisplayed()
    }

    @Test
    fun `the player opened from downloads goes back to downloads, then home`() {
        composeRule.setContent(content)

        tap("Open downloads")
        tap("Play a download")
        composeRule.onNodeWithText("Player page").assertIsDisplayed()

        systemBack()
        composeRule.onNodeWithText("Downloads page").assertIsDisplayed()
        systemBack()
        composeRule.onNodeWithText("Home taps: 0").assertIsDisplayed()
    }

    @Test
    fun `the player leaving twice at once goes back once`() {
        composeRule.setContent(content)

        tap("Open player")
        tap("Leave twice")

        composeRule.onNodeWithText("Home taps: 0").assertIsDisplayed()
    }

    @Test
    fun `the player opened from downloads, leaving twice at once, goes back to downloads`() {
        composeRule.setContent(content)

        tap("Open downloads")
        tap("Play a download")
        tap("Leave twice")

        composeRule.onNodeWithText("Downloads page").assertIsDisplayed()
    }

    @Test
    fun `opening the player while it's showing doesn't stack a second one`() {
        var openPlayer: () -> Unit = {}
        composeRule.setContent {
            AudioVaultNavigation(
                rootScreen = RootScreen.SignedIn,
                home = { navigator ->
                    openPlayer = navigator.openPlayer
                    Text("Home")
                },
                player = { _ -> Text("Player page") },
                downloads = { _, _ -> },
                settings = { },
            )
        }

        composeRule.runOnIdle { openPlayer() }
        composeRule.runOnIdle { openPlayer() }
        systemBack()

        composeRule.onNodeWithText("Home").assertIsDisplayed()
    }

    @Test
    fun `at home, back is left to the app`() {
        composeRule.setContent(content)
        composeRule.waitForIdle()

        composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks() shouldBe false
    }

    @Test
    fun `home keeps its state while covered`() {
        composeRule.setContent(content)
        tap("Home taps: 0")

        tap("Open settings")
        systemBack()

        composeRule.onNodeWithText("Home taps: 1").assertIsDisplayed()
    }

    @Test
    fun `the pages open survive a rotation`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent(content)
        tap("Open downloads")
        tap("Play a download")

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Player page").assertIsDisplayed()
        systemBack()
        composeRule.onNodeWithText("Downloads page").assertIsDisplayed()
    }

    // --- Signed out ---------------------------------------------------------------------------------

    @Test
    fun `before signing in, the downloads and a download playing open and go back as ever`() {
        root = RootScreen.Connect
        composeRule.setContent(content)

        tap("Open downloads")
        tap("Play a download")
        composeRule.onNodeWithText("Player page").assertIsDisplayed()

        systemBack()
        composeRule.onNodeWithText("Downloads page").assertIsDisplayed()
    }

    @Test
    fun `the settings close once there's no one signed in, and don't come back on signing in`() {
        composeRule.setContent(content)
        tap("Open settings")

        root = RootScreen.Connect
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Home taps: 0").assertIsDisplayed()
        composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks() shouldBe false

        root = RootScreen.SignedIn
        composeRule.onNodeWithText("Home taps: 0").assertIsDisplayed()
    }

    /** As after the app's process has gone: who's signed in is read again before the settings can show. */
    @Test
    fun `settings restored before it's known who's signed in wait for it`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent(content)
        tap("Open settings")

        root = RootScreen.Splash
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("Settings page").assertDoesNotExist()

        root = RootScreen.SignedIn
        composeRule.onNodeWithText("Settings page").assertIsDisplayed()
    }
}
