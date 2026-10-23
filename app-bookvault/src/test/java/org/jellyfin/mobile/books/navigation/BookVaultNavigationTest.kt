package org.jellyfin.mobile.books.navigation

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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.jellyfin.mobile.common.ui.RootScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BookVaultNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Where signing in has got to, as the connect screen and sign-in would have it. */
    private var root by mutableStateOf(RootScreen.SignedIn)

    /** Each reader's view model, in the order they were made. */
    private val readers = mutableListOf<ReaderProbe>()

    class ReaderProbe(val bookId: String) : ViewModel() {
        var cleared = false

        override fun onCleared() {
            cleared = true
        }
    }

    /** Stand-in screens; home has saveable state (like a scroll position) to keep while covered. */
    private val content: @Composable () -> Unit = {
        BookVaultNavigation(
            rootScreen = root,
            home = { navigator ->
                var taps by rememberSaveable { mutableIntStateOf(0) }
                Column {
                    Button(onClick = { taps++ }) { Text("Home taps: $taps") }
                    Button(onClick = { navigator.openBook("dune") }) { Text("Open Dune") }
                    Button(onClick = navigator.openSettings) { Text("Open settings") }
                }
            },
            reader = { bookId, onBack ->
                val probe = viewModel { ReaderProbe(bookId).also { readers += it } }
                Button(onClick = onBack) { Text("Reading ${probe.bookId}") }
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
    fun `a book and the settings each open over home, and back returns to it`() {
        composeRule.setContent(content)

        for ((open, page) in listOf("Open Dune" to "Reading dune", "Open settings" to "Settings page")) {
            tap(open)
            composeRule.onNodeWithText(page).assertIsDisplayed()
            systemBack()
            composeRule.onNodeWithText("Home taps: 0").assertIsDisplayed()
        }
    }

    @Test
    fun `a page's own back goes back too`() {
        composeRule.setContent(content)

        tap("Open Dune")
        tap("Reading dune")

        composeRule.onNodeWithText("Home taps: 0").assertIsDisplayed()
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

        tap("Open Dune")
        systemBack()

        composeRule.onNodeWithText("Home taps: 1").assertIsDisplayed()
    }

    @Test
    fun `the book open survives a rotation, with the same view model`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent(content)
        tap("Open Dune")

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Reading dune").assertIsDisplayed()
        readers.size shouldBe 1
        readers.single().cleared shouldBe false
        systemBack()
        composeRule.onNodeWithText("Home taps: 0").assertIsDisplayed()
    }

    @Test
    fun `closing a book lets its view model go, and opening it again starts afresh`() {
        composeRule.setContent(content)
        tap("Open Dune")
        composeRule.waitForIdle()
        val first = readers.single()

        systemBack()
        composeRule.waitForIdle()
        first.cleared shouldBe true

        tap("Open Dune")
        composeRule.waitForIdle()
        readers.size shouldBe 2
        readers.last().cleared shouldBe false
        readers.last() shouldBeSameInstanceAs readers[1]
    }

    // --- Signed out ---------------------------------------------------------------------------------

    @Test
    fun `everything open closes once there's no one signed in, and doesn't come back on signing in`() {
        composeRule.setContent(content)
        tap("Open Dune")

        composeRule.waitForIdle()
        val reader = readers.single()

        root = RootScreen.Connect
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Home taps: 0").assertIsDisplayed()
        composeRule.activity.onBackPressedDispatcher.hasEnabledCallbacks() shouldBe false
        reader.cleared shouldBe true

        root = RootScreen.SignedIn
        composeRule.onNodeWithText("Home taps: 0").assertIsDisplayed()
    }

    /** As after the app's process has gone: who's signed in is read again before the book can show. */
    @Test
    fun `a book restored before it's known who's signed in waits for it`() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent(content)
        tap("Open Dune")

        root = RootScreen.Splash
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText("Reading dune").assertDoesNotExist()

        root = RootScreen.SignedIn
        composeRule.onNodeWithText("Reading dune").assertIsDisplayed()
    }
}
