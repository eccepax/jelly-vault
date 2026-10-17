package org.jellyfin.mobile.livetv.channels

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import org.jellyfin.mobile.common.ui.components.bugReportUrl
import org.jellyfin.mobile.livetv.catalog.ChannelFilter
import org.jellyfin.mobile.livetv.catalog.channel
import org.jellyfin.mobile.livetv.catalog.program
import org.jellyfin.mobile.livetv.domain.LiveChannel
import org.jellyfin.sdk.model.ClientInfo
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
    private var signedOut = false
    private val opened = mutableListOf<String>()

    private fun loaded(
        channels: List<LiveChannel> = listOf(bulls, espn, tubi),
        filter: ChannelFilter = ChannelFilter(),
    ) = ChannelsUiState.Loaded(channels = channels, tags = listOf("Free", "My Teams", "Sports"), filter = filter)

    // Not the app's real name and version, so the menu is seen to show the ones it's given
    private val app = ClientInfo("LiveVault Test", "9.9.9")

    private fun show(state: ChannelsUiState, onTv: Boolean = false) {
        composeRule.setContent {
            // A TV has no touch screen: the remote's D-pad keeps it in keyboard input mode, which
            // it's already in when the screen appears.
            val inputMode = LocalInputModeManager.current
            var ready by remember { mutableStateOf(!onTv) }
            if (onTv) {
                LaunchedEffect(Unit) {
                    inputMode.requestInputMode(InputMode.Keyboard)
                    snapshotFlow { inputMode.inputMode }.first { it == InputMode.Keyboard }
                    ready = true
                }
            }
            val uriHandler = object : UriHandler {
                override fun openUri(uri: String) {
                    opened += uri
                }
            }
            if (ready) {
                CompositionLocalProvider(LocalUriHandler provides uriHandler) {
                    ChannelsScreen(
                        state = state,
                        now = now,
                        onTv = onTv,
                        app = app,
                        actions = ChannelsActions(
                            onQueryChange = { query = it },
                            onTagSelect = { tagSelected = it },
                            onFavoritesSelect = { favoritesSelected = true },
                            onFavoriteToggle = { favoriteToggled = it },
                            onChannelClick = { clicked = it },
                            onSignOut = { signedOut = true },
                            onRetry = { retried = true },
                        ),
                    )
                }
            }
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
    fun `choosing another tag goes back to the top of the channels`() {
        val many = List(60) { channel("${it + 1}", "Channel ${it + 1}", tags = listOf("Sports")) }
        var state by mutableStateOf(loaded(channels = many))
        composeRule.setContent {
            ChannelsScreen(state = state, now = now, app = app, actions = ChannelsActions({}, {}, {}, {}, {}, {}, {}))
        }
        composeRule.onNodeWithTag(ChannelsScreenTags.GRID).performScrollToIndex(many.lastIndex)

        state = loaded(channels = many, filter = ChannelFilter(tag = "Sports"))

        composeRule.onNodeWithText("Channel 1").assertIsDisplayed()
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

    @Test
    fun `sign out is in the menu, once confirmed`() {
        show(loaded())

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Sign out").performClick()
        signedOut shouldBe false
        composeRule.onNodeWithText("Yes, sign out").performClick()

        signedOut shouldBe true
    }

    @Test
    fun `sign out can be called off`() {
        show(loaded())

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Sign out").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        signedOut shouldBe false
        composeRule.onNodeWithText("Yes, sign out").assertDoesNotExist()
    }

    @Test
    fun `the menu says which version this is, and reporting a bug opens the form for it`() {
        show(loaded())

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("LiveVault Test 9.9.9").assertIsDisplayed()
        composeRule.onNodeWithText("Report a bug").performClick()

        opened shouldBe listOf(bugReportUrl("LiveVault Test", "9.9.9"))
    }

    @Test
    fun `sign out can be reached while the channels fail to load`() {
        show(ChannelsUiState.Error)

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Sign out").assertIsDisplayed()
    }

    @Test
    fun `offers to continue the last channel`() {
        show(loaded().copy(lastChannel = espn))

        composeRule.onNodeWithText("Continue watching").assertIsDisplayed()
        composeRule.onNodeWithTag(ChannelsScreenTags.CONTINUE).performClick()

        clicked shouldBe espn
    }

    @Test
    fun `no offer when there is nothing to continue`() {
        show(loaded())

        composeRule.onNodeWithText("Continue watching").assertDoesNotExist()
    }

    @Test
    fun `on a TV the remote starts on the channel to continue`() {
        show(loaded().copy(lastChannel = espn), onTv = true)

        composeRule.onNodeWithTag(ChannelsScreenTags.CONTINUE).assertIsFocused()
    }

    @Test
    fun `on a TV with nothing to continue the remote starts on the first channel`() {
        show(loaded(), onTv = true)

        composeRule.onNodeWithTag(ChannelsScreenTags.channel(bulls.id)).assertIsFocused()
    }

    @Test
    fun `on a phone nothing is focused`() {
        show(loaded().copy(lastChannel = espn))

        composeRule.onNodeWithTag(ChannelsScreenTags.CONTINUE).assertIsNotFocused()
        composeRule.onNodeWithTag(ChannelsScreenTags.channel(bulls.id)).assertIsNotFocused()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `on a TV the remote can go into the search box and out again`() {
        show(loaded(), onTv = true)
        composeRule.onNodeWithTag(ChannelsScreenTags.channel(bulls.id)).assertIsFocused()

        composeRule.onRoot().performKeyInput {
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionUp)
        }
        composeRule.onNodeWithTag(ChannelsScreenTags.SEARCH).assertIsFocused()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithTag(ChannelsScreenTags.SEARCH).assertIsNotFocused()
    }
}
