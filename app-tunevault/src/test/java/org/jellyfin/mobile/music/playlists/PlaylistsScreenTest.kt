package org.jellyfin.mobile.music.playlists

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.domain.Playlist
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class PlaylistsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: PlaylistsUiState) {
        composeRule.setContent {
            PlaylistsScreen(
                state = state,
                onRetry = { events += "retry" },
                onPlaylistClick = { events += "open ${it.id}" },
                onCreate = { events += "create '$it'" },
            )
        }
    }

    @Test
    fun `lists playlists with their song counts`() {
        show(PlaylistsUiState.Loaded(listOf(Playlist("road", "Road trip", 12), Playlist("one", "Single", 1))))

        composeRule.onNodeWithText("Road trip").assertIsDisplayed()
        composeRule.onNodeWithText("12 songs").assertIsDisplayed()
        composeRule.onNodeWithText("1 song").assertIsDisplayed()
    }

    @Test
    fun `tapping a playlist opens it`() {
        show(PlaylistsUiState.Loaded(listOf(Playlist("road", "Road trip", 12))))

        composeRule.onNodeWithText("Road trip").performClick()

        events shouldBe listOf("open road")
    }

    @Test
    fun `new playlist asks for a name and creates it`() {
        show(PlaylistsUiState.Loaded(emptyList()))

        composeRule.onNodeWithText("New playlist").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Gym")
        composeRule.onNodeWithText("Create").performClick()

        events shouldBe listOf("create 'Gym'")
    }

    @Test
    fun `cancelling a new playlist creates nothing`() {
        show(PlaylistsUiState.Loaded(emptyList()))

        composeRule.onNodeWithText("New playlist").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Gym")
        composeRule.onNodeWithText("Cancel").performClick()

        events shouldBe emptyList()
        composeRule.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test
    fun `no playlists says so and still offers to make one`() {
        show(PlaylistsUiState.Loaded(emptyList()))

        composeRule.onNodeWithText("No playlists yet").assertIsDisplayed()
        composeRule.onNodeWithText("New playlist").assertIsDisplayed()
    }

    @Test
    fun `an error offers a retry`() {
        show(PlaylistsUiState.Error)

        composeRule.onNodeWithText("Retry").performClick()

        events shouldBe listOf("retry")
    }
}
