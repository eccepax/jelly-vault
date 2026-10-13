package org.jellyfin.mobile.music.library

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.domain.track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class SongsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val songs = listOf(
        track(
            "a",
        ).copy(title = "Around the World", artist = "Daft Punk", albumTitle = "Homework", durationMs = 429_000L),
        track("w").copy(title = "White & Nerdy", artist = "", albumTitle = "", durationMs = 0L),
    )
    private val events = mutableListOf<String>()
    private var downloadedTrackIds = emptySet<String>()

    private var currentTrackId: String? = null

    private fun show(state: SongsUiState) {
        composeRule.setContent {
            SongsScreen(
                state = state,
                onRetry = { events += "retry" },
                onSongClick = { events += "play $it" },
                onShuffleAll = { events += "shuffle" },
                trackActions = TrackListActions(
                    onPlayNext = { events += "playNext ${it.id}" },
                    onAddToQueue = { events += "addToQueue ${it.id}" },
                    currentTrackId = currentTrackId,
                    onAddToPlaylist = { events += "addToPlaylist ${it.id}" },
                    downloadedTrackIds = downloadedTrackIds,
                ),
            )
        }
    }

    @Test
    fun `lists songs with artist, album and duration`() {
        show(SongsUiState.Loaded(songs))

        composeRule.onNodeWithText("Around the World").assertIsDisplayed()
        composeRule.onNodeWithText("Daft Punk · Homework").assertIsDisplayed()
        composeRule.onNodeWithText("7:09").assertIsDisplayed()
    }

    @Test
    fun `untagged songs say unknown artist and show no duration`() {
        show(SongsUiState.Loaded(songs))

        composeRule.onNodeWithText("Unknown artist").assertIsDisplayed()
        composeRule.onNodeWithText("0:00").assertDoesNotExist()
    }

    @Test
    fun `tapping a song plays from it, and shuffle all shuffles`() {
        show(SongsUiState.Loaded(songs))

        composeRule.onNodeWithText("White & Nerdy").performClick()
        composeRule.onNodeWithText("Shuffle all").performClick()

        events shouldBe listOf("play 1", "shuffle")
    }

    @Test
    fun `long-pressing a song offers play next and add to queue`() {
        show(SongsUiState.Loaded(songs))

        composeRule.onNodeWithText("Around the World").performTouchInput { longClick() }
        composeRule.onNodeWithText("Play next").performClick()
        composeRule.onNodeWithText("White & Nerdy").performTouchInput { longClick() }
        composeRule.onNodeWithText("Add to queue").performClick()

        events shouldBe listOf("playNext a", "addToQueue w")
    }

    @Test
    fun `long-pressing a song offers add to playlist`() {
        show(SongsUiState.Loaded(songs))

        composeRule.onNodeWithText("Around the World").performTouchInput { longClick() }
        composeRule.onNodeWithText("Add to playlist…").performClick()

        events shouldBe listOf("addToPlaylist a")
    }

    @Test
    fun `the playing song is marked`() {
        currentTrackId = "w"
        show(SongsUiState.Loaded(songs))

        composeRule.onAllNodesWithContentDescription("Now playing").assertCountEquals(1)
        // The row is one clickable unit, so its merged node carries both the title and the marker.
        composeRule.onNode(hasText("White & Nerdy") and hasContentDescription("Now playing")).assertExists()
    }

    @Test
    fun `no songs says so`() {
        show(SongsUiState.Loaded(emptyList()))

        composeRule.onNodeWithText("No songs found").assertIsDisplayed()
    }

    @Test
    fun `downloaded songs are marked`() {
        downloadedTrackIds = setOf("a")
        show(SongsUiState.Loaded(songs))

        composeRule.onAllNodesWithContentDescription("Downloaded").assertCountEquals(1)
    }
}
