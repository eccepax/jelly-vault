package org.jellyfin.mobile.music.nowplaying

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.domain.RepeatMode
import org.jellyfin.mobile.music.domain.track
import org.jellyfin.mobile.music.playback.SleepTimerState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class NowPlayingScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val londonMay = track("2").copy(
        title = "London May",
        artist = "Bonnie \"Prince\" Billy",
        albumTitle = "The Purple Bird",
        durationMs = 180_000L,
    )
    private val playing = NowPlayingUiState(
        track = londonMay,
        isPlaying = true,
        positionMs = 65_000L,
        durationMs = 180_000L,
    )
    private val events = mutableListOf<String>()

    private fun show(state: NowPlayingUiState) {
        composeRule.setContent {
            NowPlayingScreen(
                state = state,
                actions = NowPlayingActions(
                    onCollapse = { events += "collapse" },
                    onTogglePlayPause = { events += "toggle" },
                    onNext = { events += "next" },
                    onPrevious = { events += "previous" },
                    onSeek = { events += "seek $it" },
                    onCycleRepeat = { events += "repeat" },
                    onToggleShuffle = { events += "shuffle" },
                    onOpenQueue = { events += "queue" },
                    onToggleFavorite = { events += "favorite" },
                ),
            )
        }
    }

    @Test
    fun `shows the track, artist and album`() {
        show(playing)

        composeRule.onNodeWithText("London May").assertIsDisplayed()
        composeRule.onNodeWithText("Bonnie \"Prince\" Billy").assertIsDisplayed()
        composeRule.onNodeWithText("The Purple Bird").assertIsDisplayed()
    }

    @Test
    fun `shows elapsed and total time`() {
        show(playing)

        composeRule.onNodeWithText("1:05").assertIsDisplayed()
        composeRule.onNodeWithText("3:00").assertIsDisplayed()
    }

    @Test
    fun `transport buttons`() {
        show(playing)

        composeRule.onNodeWithContentDescription("Previous").performClick()
        composeRule.onNodeWithContentDescription("Pause").performClick()
        composeRule.onNodeWithContentDescription("Next").performClick()
        composeRule.onNodeWithContentDescription("Collapse").performClick()

        events shouldBe listOf("previous", "toggle", "next", "collapse")
    }

    @Test
    fun `the heart adds a song to favorites`() {
        show(playing.copy(isFavorite = false))

        composeRule.onNodeWithContentDescription("Add to favorites").performClick()

        events shouldBe listOf("favorite")
    }

    @Test
    fun `a favorite song's heart offers to remove it`() {
        show(playing.copy(isFavorite = true))

        composeRule.onNodeWithContentDescription("Remove from favorites").assertIsDisplayed()
    }

    @Test
    fun `the queue button opens the queue`() {
        show(playing)

        composeRule.onNodeWithContentDescription("Queue").performClick()

        events shouldBe listOf("queue")
    }

    @Test
    fun `dragging the seek bar seeks to that point`() {
        show(playing)

        composeRule.onNodeWithContentDescription(
            "Seek",
        ).performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }

        events shouldBe listOf("seek 90000")
    }

    @Test
    fun `shuffle button shows its state and toggles`() {
        show(playing.copy(isShuffled = false))
        composeRule.onNodeWithContentDescription("Shuffle off").performClick()

        events shouldBe listOf("shuffle")
    }

    @Test
    fun `shuffle on is labelled as on`() {
        show(playing.copy(isShuffled = true))

        composeRule.onNodeWithContentDescription("Shuffle on").assertIsDisplayed()
    }

    @Test
    fun `repeat button shows each mode and cycles`() {
        show(playing.copy(repeatMode = RepeatMode.ONE))
        composeRule.onNodeWithContentDescription("Repeat one").performClick()

        events shouldBe listOf("repeat")
    }

    @Test
    fun `repeat all and off are labelled`() {
        show(playing.copy(repeatMode = RepeatMode.ALL))
        composeRule.onNodeWithContentDescription("Repeat all").assertIsDisplayed()
    }

    @Test
    fun `the sleep timer is on now playing, with its time left`() {
        composeRule.setContent {
            NowPlayingScreen(
                state = NowPlayingUiState(),
                actions = NowPlayingActions(),
                sleepTimer = SleepTimerUi(state = SleepTimerState.Counting(remainingMs = 600_000L)),
            )
        }

        composeRule.onNodeWithContentDescription("Sleep timer").assertIsDisplayed()
        composeRule.onNodeWithText("Sleeps in 10:00").assertIsDisplayed()
    }

    @Test
    fun `a hidden sleep timer isn't on now playing`() {
        composeRule.setContent {
            NowPlayingScreen(state = NowPlayingUiState(), actions = NowPlayingActions(), sleepTimer = null)
        }

        composeRule.onNodeWithContentDescription("Sleep timer").assertDoesNotExist()
    }
}
