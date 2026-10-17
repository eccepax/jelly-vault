package org.jellyfin.mobile.music.navigation

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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LibraryNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** Stand-in screens; the home screen has saveable state (like a scroll position) to preserve. */
    private val content: @Composable () -> Unit = {
        LibraryNavigation(
            home = { browse ->
                var taps by rememberSaveable { mutableIntStateOf(0) }
                Column {
                    Button(onClick = { taps++ }) { Text("Home taps: $taps") }
                    Button(onClick = { browse.openAlbum("pb") }) { Text("Open Purple Bird") }
                    Button(onClick = { browse.openArtist("k") }) { Text("Open Kendrick") }
                    Button(onClick = { browse.openPlaylist("road") }) { Text("Open Road trip") }
                    Button(onClick = browse.openSettings) { Text("Open settings") }
                }
            },
            albumDetail = { albumId, onBack -> Button(onClick = onBack) { Text("Album $albumId") } },
            artistDetail = { artistId, onBack, browse ->
                Column {
                    Button(onClick = onBack) { Text("Artist $artistId") }
                    Button(onClick = { browse.openAlbum("damn") }) { Text("Open Damn") }
                }
            },
            playlistDetail = { playlistId, onBack -> Button(onClick = onBack) { Text("Playlist $playlistId") } },
            settings = { onBack -> Button(onClick = onBack) { Text("Settings page") } },
        )
    }

    private fun systemBack() {
        composeRule.waitForIdle() // BackHandler enables on the recomposition after a navigation
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    }

    @Test
    fun `a page asked for from outside, like the player, opens on top, and back returns home`() {
        var target by mutableStateOf<BrowseTarget?>(null)
        composeRule.setContent {
            LibraryNavigation(
                home = { Text("Home") },
                albumDetail = { albumId, _ -> Text("Album $albumId") },
                artistDetail = { artistId, _, _ -> Text("Artist $artistId") },
                playlistDetail = { _, _ -> },
                settings = { },
                open = target,
                onOpened = { target = null },
            )
        }

        target = BrowseTarget.Artist("bonnie")

        composeRule.onNodeWithText("Artist bonnie").assertIsDisplayed()
        target shouldBe null
        systemBack()
        composeRule.onNodeWithText("Home").assertIsDisplayed()
    }

    @Test
    fun `starts on the library home`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Open Purple Bird").assertIsDisplayed()
    }

    @Test
    fun `opening an album shows it, and its back returns home`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Open Purple Bird").performClick()
        composeRule.onNodeWithText("Album pb").performClick()

        composeRule.onNodeWithText("Open Purple Bird").assertIsDisplayed()
    }

    @Test
    fun `opening settings shows them, and back returns home`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Open settings").performClick()
        composeRule.onNodeWithText("Settings page").assertIsDisplayed()

        systemBack()
        composeRule.onNodeWithText("Open settings").assertIsDisplayed()
    }

    @Test
    fun `opening a playlist shows it, and back returns home`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Open Road trip").performClick()
        composeRule.onNodeWithText("Playlist road").assertIsDisplayed()

        systemBack()
        composeRule.onNodeWithText("Open Road trip").assertIsDisplayed()
    }

    @Test
    fun `artist then album, and back retraces each step`() {
        composeRule.setContent(content)

        composeRule.onNodeWithText("Open Kendrick").performClick()
        composeRule.onNodeWithText("Open Damn").performClick()
        composeRule.onNodeWithText("Album damn").assertIsDisplayed()

        systemBack()
        composeRule.onNodeWithText("Artist k").assertIsDisplayed()

        systemBack()
        composeRule.onNodeWithText("Open Kendrick").assertIsDisplayed()
    }

    @Test
    fun `home keeps its saved state, such as scroll position, while covered`() {
        composeRule.setContent(content)
        composeRule.onNodeWithText("Home taps: 0").performClick()
        composeRule.onNodeWithText("Home taps: 1").performClick()

        composeRule.onNodeWithText("Open Kendrick").performClick()
        composeRule.onNodeWithText("Open Damn").performClick()
        systemBack()
        systemBack()

        composeRule.onNodeWithText("Home taps: 2").assertIsDisplayed()
    }

    @Test
    fun `the whole stack survives recreation`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent(content)
        composeRule.onNodeWithText("Open Kendrick").performClick()
        composeRule.onNodeWithText("Open Damn").performClick()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText("Album damn").assertIsDisplayed()
        systemBack()
        composeRule.onNodeWithText("Artist k").assertIsDisplayed()
    }
}
