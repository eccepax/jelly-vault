package org.jellyfin.mobile.music.nowplaying

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.domain.track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class MiniPlayerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val londonMay = track("2").copy(title = "London May", artist = "Bonnie \"Prince\" Billy")
    private val events = mutableListOf<String>()

    private fun show(state: NowPlayingUiState) {
        composeRule.setContent {
            MiniPlayer(
                state = state,
                onTogglePlayPause = { events += "toggle" },
                onNext = { events += "next" },
                onExpand = { events += "expand" },
            )
        }
    }

    @Test
    fun `shows the playing track`() {
        show(NowPlayingUiState(track = londonMay, isPlaying = true))

        composeRule.onNodeWithText("London May").assertIsDisplayed()
        composeRule.onNodeWithText("Bonnie \"Prince\" Billy").assertIsDisplayed()
    }

    @Test
    fun `a track with no artist says unknown artist`() {
        show(NowPlayingUiState(track = londonMay.copy(artist = ""), isPlaying = true))

        composeRule.onNodeWithText("Unknown artist").assertIsDisplayed()
    }

    @Test
    fun `offers pause while playing and play while paused`() {
        show(NowPlayingUiState(track = londonMay, isPlaying = true))
        composeRule.onNodeWithContentDescription("Pause").performClick()

        events shouldBe listOf("toggle")
    }

    @Test
    fun `offers play while paused`() {
        show(NowPlayingUiState(track = londonMay, isPlaying = false))

        composeRule.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun `next skips ahead`() {
        show(NowPlayingUiState(track = londonMay, isPlaying = true))

        composeRule.onNodeWithContentDescription("Next").performClick()

        events shouldBe listOf("next")
    }

    @Test
    fun `tapping the bar expands to now playing`() {
        show(NowPlayingUiState(track = londonMay, isPlaying = true))

        composeRule.onNodeWithText("London May").performClick()

        events shouldBe listOf("expand")
    }

    @Test
    fun `hidden when nothing is playing`() {
        show(NowPlayingUiState(track = null))

        composeRule.onNodeWithTag(MiniPlayerTags.ROOT).assertDoesNotExist()
    }
}
