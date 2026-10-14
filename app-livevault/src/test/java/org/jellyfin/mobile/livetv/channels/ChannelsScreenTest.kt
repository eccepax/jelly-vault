package org.jellyfin.mobile.livetv.channels

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.livetv.catalog.ChannelFilter
import org.jellyfin.mobile.livetv.catalog.channel
import org.jellyfin.mobile.livetv.catalog.program
import org.jellyfin.mobile.livetv.domain.LiveChannel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

// Plain Application: the real LiveVaultApplication would start Koin, which this stateless screen
// doesn't need. Phone-sized screen (the A52ish emulator's).
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class ChannelsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-10-14T19:00:00Z")
    private val bulls = channel("1", "Bulls", tags = listOf("My Teams"), now = program("Bulls at Pacers"))
    private val espn = channel("43", "ESPN", tags = listOf("Sports"), favorite = true)
    private val tubi = channel("410", "FREE| MLB", tags = listOf("Free"))

    private var query: String? = null
    private var tagSelected: String? = "unset"
    private var favoritesSelected = false
    private var favoriteToggled: LiveChannel? = null
    private var clicked: LiveChannel? = null
    private var retried = false

    private fun loaded(
        channels: List<LiveChannel> = listOf(bulls, espn, tubi),
        filter: ChannelFilter = ChannelFilter(),
    ) = ChannelsUiState.Loaded(channels = channels, tags = listOf("Free", "My Teams", "Sports"), filter = filter)

    private fun show(state: ChannelsUiState) {
        composeRule.setContent {
            ChannelsScreen(
                state = state,
                now = now,
                onQueryChange = { query = it },
                onTagSelect = { tagSelected = it },
                onFavoritesSelect = { favoritesSelected = true },
                onFavoriteToggle = { favoriteToggled = it },
                onChannelClick = { clicked = it },
                onRetry = { retried = true },
            )
        }
    }

    @Test
    fun `shows a progress indicator while loading`() {
        show(ChannelsUiState.Loading)

        composeRule.onNodeWithTag(ChannelsScreenTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun `a failed load offers retry`() {
        show(ChannelsUiState.Error)

        composeRule.onNodeWithText("Couldn't load channels").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        retried shouldBe true
    }

    @Test
    fun `shows each channel's number, name and what is on now`() {
        show(loaded())

        composeRule.onNodeWithText("1").assertIsDisplayed()
        composeRule.onNodeWithText("Bulls").assertIsDisplayed()
        composeRule.onNodeWithText("Bulls at Pacers").assertIsDisplayed()
        composeRule.onNodeWithText("ESPN").assertIsDisplayed()
    }

    @Test
    fun `free channels are named without their prefix`() {
        show(loaded())

        composeRule.onNodeWithText("MLB").assertIsDisplayed()
    }

    @Test
    fun `a channel with no guide says so`() {
        show(loaded(channels = listOf(espn)))

        composeRule.onNodeWithText("No guide information").assertIsDisplayed()
    }

    @Test
    fun `tapping a channel opens it`() {
        show(loaded())

        composeRule.onNodeWithText("ESPN").performClick()

        clicked shouldBe espn
    }

    @Test
    fun `the star says whether a channel is a favorite and toggles it`() {
        show(loaded(channels = listOf(espn)))

        composeRule.onNodeWithContentDescription("Remove ESPN from favorites").performClick()

        favoriteToggled shouldBe espn
    }

    @Test
    fun `a channel that is not a favorite can be added`() {
        show(loaded(channels = listOf(bulls)))

        composeRule.onNodeWithContentDescription("Add Bulls to favorites").performClick()

        favoriteToggled shouldBe bulls
    }

    @Test
    fun `all is selected when there is no filter`() {
        show(loaded())

        composeRule.onNodeWithText("All").assertIsSelected()
        composeRule.onNodeWithText("Sports").assertIsNotSelected()
    }

    @Test
    fun `the selected tag is shown selected`() {
        show(loaded(filter = ChannelFilter(tag = "Sports")))

        composeRule.onNodeWithText("Sports").assertIsSelected()
        composeRule.onNodeWithText("All").assertIsNotSelected()
    }

    @Test
    fun `tapping a tag selects it`() {
        show(loaded())

        composeRule.onNodeWithText("My Teams").performClick()

        tagSelected shouldBe "My Teams"
    }

    @Test
    fun `tapping all clears the tag`() {
        show(loaded(filter = ChannelFilter(tag = "Sports")))

        composeRule.onNodeWithText("All").performClick()

        tagSelected shouldBe null
    }

    @Test
    fun `tapping favorites shows favorites`() {
        show(loaded())

        composeRule.onNodeWithText("Favorites").performClick()

        favoritesSelected shouldBe true
    }

    @Test
    fun `typing searches`() {
        show(loaded())

        composeRule.onNodeWithTag(ChannelsScreenTags.SEARCH).performTextInput("espn")

        query shouldBe "espn"
    }

    @Test
    fun `no favorites yet explains how to add one`() {
        show(loaded(channels = emptyList(), filter = ChannelFilter(favoritesOnly = true)))

        composeRule.onNodeWithText("No favorite channels yet. Tap the star on a channel to add it.").assertIsDisplayed()
    }

    @Test
    fun `a search with no matches says so`() {
        show(loaded(channels = emptyList(), filter = ChannelFilter(query = "zzz")))

        composeRule.onNodeWithText("No channels match \"zzz\"").assertIsDisplayed()
    }

    @Test
    fun `the list lays out more than its first channel`() {
        // A lazy list composes its first item even at zero size, so only a later channel proves
        // the list actually has room on screen.
        show(loaded())

        composeRule.onNodeWithText("MLB").assertIsDisplayed()
    }
}
