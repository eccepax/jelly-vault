package org.jellyfin.mobile.music.playlists

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.domain.Playlist
import org.jellyfin.mobile.music.domain.PlaylistEntry
import org.jellyfin.mobile.music.domain.track
import org.jellyfin.mobile.music.library.TrackListActions
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class PlaylistDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val loaded = PlaylistDetailUiState.Loaded(
        playlist = Playlist("road", "Road trip", 2),
        entries = listOf(
            PlaylistEntry("e1", track("a").copy(title = "Around the World")),
            PlaylistEntry("e2", track("b").copy(title = "Da Funk")),
        ),
    )
    private val events = mutableListOf<String>()

    private fun show(state: PlaylistDetailUiState) {
        composeRule.setContent {
            PlaylistDetailScreen(
                state = state,
                onBack = { events += "back" },
                onPlay = { events += "play" },
                onShuffle = { events += "shuffle" },
                onTrackClick = { events += "track $it" },
                onRemove = { events += "remove $it" },
                trackActions = TrackListActions(onPlayNext = { events += "playNext ${it.id}" }),
            )
        }
    }

    @Test
    fun `shows the playlist's name and songs`() {
        show(loaded)

        composeRule.onNodeWithText("Road trip").assertIsDisplayed()
        composeRule.onNodeWithText("2 songs").assertIsDisplayed()
        composeRule.onNodeWithText("Da Funk").assertIsDisplayed()
    }

    @Test
    fun `play, shuffle, tap and back`() {
        show(loaded)

        composeRule.onNodeWithText("Play").performClick()
        composeRule.onNodeWithText("Shuffle").performClick()
        composeRule.onNodeWithText("Da Funk").performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        events shouldBe listOf("play", "shuffle", "track 1", "back")
    }

    @Test
    fun `long-press offers remove from playlist alongside the queue actions`() {
        show(loaded)

        composeRule.onNodeWithText("Da Funk").performTouchInput { longClick() }
        composeRule.onNodeWithText("Play next").assertIsDisplayed()
        composeRule.onNodeWithText("Remove from playlist").performClick()

        events shouldBe listOf("remove 1")
    }

    @Test
    fun `an empty playlist says so`() {
        show(loaded.copy(entries = emptyList()))

        composeRule.onNodeWithText("This playlist is empty").assertIsDisplayed()
    }

    @Test
    fun `an unknown playlist says so`() {
        show(PlaylistDetailUiState.NotFound)

        composeRule.onNodeWithText("Playlist not found").assertIsDisplayed()
    }
}
