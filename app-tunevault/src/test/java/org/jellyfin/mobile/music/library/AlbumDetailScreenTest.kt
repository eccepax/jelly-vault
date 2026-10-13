package org.jellyfin.mobile.music.library

import android.app.Application
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.catalog.album
import org.jellyfin.mobile.music.domain.track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class AlbumDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val purpleBird = album("pb", "The Purple Bird", "Bonnie \"Prince\" Billy", year = 2025)
    private val tracks = listOf(
        track("1").copy(title = "Our Home", durationMs = 185_000L, trackNumber = 1),
        track("2").copy(title = "Turn Your Eyes", durationMs = 240_000L, trackNumber = 2),
        track("3").copy(title = "London May", durationMs = 61_000L, trackNumber = 3),
    )
    private val loaded = AlbumDetailUiState.Loaded(purpleBird, tracks)

    private var back = 0
    private var retried = 0
    private var played = 0
    private var shuffled = 0
    private var trackClicked: Int? = null
    private val actions = mutableListOf<String>()
    private var currentTrackId: String? = null
    private var isFavorite = false

    private fun show(state: AlbumDetailUiState) {
        composeRule.setContent {
            AlbumDetailScreen(
                state = state,
                onBack = { back++ },
                onRetry = { retried++ },
                onPlay = { played++ },
                onShuffle = { shuffled++ },
                onTrackClick = { trackClicked = it },
                trackActions = TrackListActions(
                    onPlayNext = { actions += "playNext ${it.id}" },
                    onAddToQueue = { actions += "addToQueue ${it.id}" },
                    currentTrackId = currentTrackId,
                ),
                favorite = FavoriteUi(isFavorite) { actions += "favorite" },
            )
        }
    }

    @Test
    fun `shows the album header`() {
        show(loaded)

        composeRule.onNodeWithText("The Purple Bird").assertIsDisplayed()
        composeRule.onNodeWithText("Bonnie \"Prince\" Billy").assertIsDisplayed()
        composeRule.onNodeWithText("2025 · 3 tracks · 8:06").assertIsDisplayed()
    }

    @Test
    fun `an album with no album artist says unknown artist`() {
        show(AlbumDetailUiState.Loaded(purpleBird.copy(artist = ""), tracks))

        composeRule.onNodeWithText("Unknown artist").assertIsDisplayed()
    }

    @Test
    fun `lists every track with its number and duration`() {
        show(loaded)

        composeRule.onNodeWithText("London May").assertIsDisplayed()
        composeRule.onNodeWithText("1:01").assertIsDisplayed()
        composeRule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun `a track of unknown length shows no duration rather than 0 colon 00`() {
        show(AlbumDetailUiState.Loaded(purpleBird, listOf(track("1").copy(title = "Mystery", durationMs = 0L))))

        composeRule.onNodeWithText("Mystery").assertIsDisplayed()
        composeRule.onNodeWithText("0:00").assertDoesNotExist()
    }

    @Test
    fun `tapping a track plays from that track`() {
        show(loaded)

        composeRule.onNodeWithText("Turn Your Eyes").performClick()

        trackClicked shouldBe 1
    }

    @Test
    fun `long-pressing a track offers queue actions`() {
        show(loaded)

        composeRule.onNodeWithText("London May").performTouchInput { longClick() }
        composeRule.onNodeWithText("Add to queue").performClick()

        actions shouldBe listOf("addToQueue 3")
    }

    @Test
    fun `the playing track is marked`() {
        currentTrackId = "2"
        show(loaded)

        composeRule.onAllNodesWithContentDescription("Now playing").assertCountEquals(1)
    }

    @Test
    fun `the album's heart adds it to favorites`() {
        show(loaded)

        composeRule.onNodeWithContentDescription("Add to favorites").performClick()

        actions shouldBe listOf("favorite")
    }

    @Test
    fun `a favorite album's heart offers to remove it`() {
        isFavorite = true
        show(loaded)

        composeRule.onNodeWithContentDescription("Remove from favorites").assertIsDisplayed()
    }

    @Test
    fun `play and shuffle buttons`() {
        show(loaded)

        composeRule.onNodeWithText("Play").performClick()
        composeRule.onNodeWithText("Shuffle").performClick()

        played shouldBe 1
        shuffled shouldBe 1
    }

    @Test
    fun `back button goes back`() {
        show(loaded)

        composeRule.onNodeWithContentDescription("Back").performClick()

        back shouldBe 1
    }

    @Test
    fun `loading shows a progress indicator`() {
        show(AlbumDetailUiState.Loading)

        composeRule.onNodeWithTag(AlbumDetailScreenTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun `an unknown album says so`() {
        show(AlbumDetailUiState.NotFound)

        composeRule.onNodeWithText("Album not found").assertIsDisplayed()
    }

    @Test
    fun `an error offers a retry`() {
        show(AlbumDetailUiState.Error)

        composeRule.onNodeWithText("Couldn't load this album").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        retried shouldBe 1
    }
}
