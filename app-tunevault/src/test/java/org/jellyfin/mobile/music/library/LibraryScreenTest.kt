package org.jellyfin.mobile.music.library

import android.app.Application
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class LibraryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val content: @Composable () -> Unit = {
        LibraryScreen(
            albums = { Text("Albums content") },
            artists = { Text("Artists content") },
            songs = { Text("Songs content") },
            playlists = { Text("Playlists content") },
            downloaded = { Text("Downloaded content") },
        )
    }

    @Test
    fun `opens on albums under a library header`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Library").assertIsDisplayed()
        composeRule.onNodeWithText("Albums").assertIsSelected()
        composeRule.onNodeWithText("Albums content").assertIsDisplayed()
    }

    @Test
    fun `tabs switch between albums, artists and songs`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Artists").performClick()
        composeRule.onNodeWithText("Artists content").assertIsDisplayed()
        composeRule.onNodeWithText("Albums content").assertDoesNotExist()

        composeRule.onNodeWithText("Songs").performClick()
        composeRule.onNodeWithText("Songs content").assertIsDisplayed()
        composeRule.onNodeWithText("Songs").assertIsSelected()
    }

    @Test
    fun `there is a playlists tab`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Playlists").performClick()

        composeRule.onNodeWithText("Playlists content").assertIsDisplayed()
    }

    @Test
    fun `the selected tab survives recreation`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent(content)
        composeRule.onNodeWithText("Songs").performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Songs content").assertIsDisplayed()
    }

    @Test
    fun `there is a downloaded tab`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Downloaded").performScrollTo().performClick()

        composeRule.onNodeWithText("Downloaded content").assertIsDisplayed()
    }
}
