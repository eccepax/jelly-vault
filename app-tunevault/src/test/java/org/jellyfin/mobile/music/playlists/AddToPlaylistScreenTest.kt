package org.jellyfin.mobile.music.playlists

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.domain.Playlist
import org.jellyfin.mobile.music.domain.track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class AddToPlaylistScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val picking = AddToPlaylistUiState.Picking(
        track = track("s").copy(title = "Da Funk"),
        playlists = listOf(Playlist("road", "Road trip", 3)),
    )
    private val events = mutableListOf<String>()

    private fun show() {
        composeRule.setContent {
            AddToPlaylistScreen(
                state = picking,
                onPick = { events += "pick $it" },
                onCreate = { events += "create '$it'" },
                onDismiss = { events += "dismiss" },
            )
        }
    }

    @Test
    fun `names the song being added`() {
        show()

        composeRule.onNodeWithText("Add “Da Funk” to…").assertIsDisplayed()
    }

    @Test
    fun `picking a playlist adds to it`() {
        show()

        composeRule.onNodeWithText("Road trip").performClick()

        events shouldBe listOf("pick road")
    }

    @Test
    fun `a new playlist can be named and made`() {
        show()

        composeRule.onNodeWithText("New playlist").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Gym")
        composeRule.onNodeWithText("Create").performClick()

        events shouldBe listOf("create 'Gym'")
    }

    @Test
    fun `close dismisses`() {
        show()

        composeRule.onNodeWithContentDescription("Close").performClick()

        events shouldBe listOf("dismiss")
    }
}
