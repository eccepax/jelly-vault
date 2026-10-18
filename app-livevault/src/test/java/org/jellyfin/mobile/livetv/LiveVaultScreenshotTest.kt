package org.jellyfin.mobile.livetv

import android.app.Application
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.first
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.FocusOutlined
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.livetv.catalog.ChannelFilter
import org.jellyfin.mobile.livetv.catalog.channel
import org.jellyfin.mobile.livetv.channels.ChannelsActions
import org.jellyfin.mobile.livetv.channels.ChannelsScreen
import org.jellyfin.mobile.livetv.channels.ChannelsScreenTags
import org.jellyfin.mobile.livetv.channels.ChannelsUiState
import org.jellyfin.mobile.livetv.domain.LiveChannel
import org.jellyfin.mobile.livetv.domain.LiveProgram
import org.jellyfin.mobile.livetv.playback.ChannelSession
import org.jellyfin.mobile.livetv.playback.LoginInUse
import org.jellyfin.mobile.livetv.playback.PlaybackState
import org.jellyfin.mobile.livetv.playback.PlayerScreen
import org.jellyfin.mobile.livetv.playback.WatchUiState
import org.jellyfin.mobile.testsupport.PHONE_QUALIFIERS
import org.jellyfin.mobile.testsupport.SCREENSHOT_SDK
import org.jellyfin.mobile.testsupport.captureWithSystemBars
import org.jellyfin.sdk.model.ClientInfo
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.util.TimeZone

/**
 * LiveVault's screens on a phone, a tablet and a Google TV, and the player; see [captureWithSystemBars].
 * Program end times show in the device's time zone, so it's pinned.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [SCREENSHOT_SDK], qualifiers = PHONE_QUALIFIERS)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveVaultScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var zone: TimeZone

    @Before
    fun pinTimeZone() {
        zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(zone)
    }

    private val content: View
        get() = composeRule.activity.findViewById(android.R.id.content)

    private fun capture(name: String) {
        composeRule.waitForIdle()
        content.captureWithSystemBars(name)
    }

    /** For screens with no system bars over them: a TV, and the full-screen player. */
    private fun captureBare(name: String) {
        composeRule.waitForIdle()
        content.captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun channels() {
        setChannels()
        capture("channels")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-night-xhdpi")
    fun `channels on a tablet`() {
        setChannels()
        capture("channels-tablet")
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-television-night-xhdpi")
    fun `channels on a TV`() {
        setChannels(onTv = true)
        // The card the remote starts on is outlined, or the screenshot shows a TV nobody could drive
        composeRule.onNodeWithTag(ChannelsScreenTags.CONTINUE).assert(SemanticsMatcher.expectValue(FocusOutlined, true))
        captureBare("channels-tv")
    }

    @Test
    @Config(qualifiers = "w852dp-h393dp-night-xhdpi")
    fun `player when the provider login is in use`() {
        val state = WatchUiState(
            playback = PlaybackState.Failed(DEVILS.id),
            channel = DEVILS,
            videoStarted = false,
            loginInUse = LoginInUse(
                listOf(ChannelSession(deviceName = "Living Room TV", userName = "sam", channelName = "Rangers")),
            ),
        )
        composeRule.setContent {
            AppTheme(VaultBrand.LiveVault) {
                PlayerScreen(
                    state = state,
                    onZap = {},
                    onRetry = {},
                    onExit = {},
                    onShowFree = {},
                    video = { NoVideo() },
                )
            }
        }
        captureBare("player-login-in-use")
    }

    private fun setChannels(onTv: Boolean = false) {
        val state = ChannelsUiState.Loaded(
            channels = listOf(
                DEVILS,
                channel("1", "Rangers", tags = listOf("My Teams", "Sports"), now = program("Rangers at Bruins")),
                channel("7", "Sports Center", tags = listOf("Sports"), now = program("Postgame Live")),
                channel("12", "City News", tags = listOf("News"), now = program("Evening News")),
                channel("44", "FREE| Classic Movies", tags = listOf("Free"), now = program("The Big Sleep")),
                channel("58", "Weather Now", tags = listOf("News")),
            ),
            tags = listOf("My Teams", "Sports", "News", "Free"),
            filter = ChannelFilter(),
            lastChannel = DEVILS,
        )
        composeRule.setContent {
            AppTheme(VaultBrand.LiveVault) {
                // A TV is driven by a remote, never touched: only then does focus show
                val inputMode = LocalInputModeManager.current
                var ready by remember { mutableStateOf(!onTv) }
                LaunchedEffect(Unit) {
                    if (onTv) {
                        inputMode.requestInputMode(InputMode.Keyboard)
                        snapshotFlow { inputMode.inputMode }.first { it == InputMode.Keyboard }
                        ready = true
                    }
                }
                if (ready) ChannelsScreen(state = state, now = NOW, onTv = onTv, app = APP, actions = NO_ACTIONS)
            }
        }
    }

    /** Where the video goes; Robolectric can't decode any. */
    @Composable
    private fun NoVideo() {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        )
    }

    private companion object {
        /** 7:30 PM in New York. */
        val NOW: Instant = Instant.parse("2026-10-18T23:30:00Z")

        fun program(title: String) = LiveProgram(
            title = title,
            start = NOW.minusSeconds(45 * 60),
            end = NOW.plusSeconds(30 * 60),
        )

        val DEVILS: LiveChannel = channel(
            "4.1",
            "Devils",
            tags = listOf("My Teams", "Sports"),
            favorite = true,
            now = program("Devils at Blackhawks"),
        )

        val APP = ClientInfo("LiveVault", "1.0.0")

        val NO_ACTIONS = ChannelsActions(
            onQueryChange = {},
            onTagSelect = {},
            onFavoritesSelect = {},
            onFavoriteToggle = {},
            onChannelClick = {},
            onRetry = {},
            onSignOut = {},
        )
    }
}
