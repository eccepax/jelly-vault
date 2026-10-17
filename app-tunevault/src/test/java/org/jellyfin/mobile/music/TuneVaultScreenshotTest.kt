package org.jellyfin.mobile.music

import android.app.Application
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.music.catalog.album
import org.jellyfin.mobile.music.domain.track
import org.jellyfin.mobile.music.home.HomeScreen
import org.jellyfin.mobile.music.home.HomeUiState
import org.jellyfin.mobile.music.navigation.MainBottomBar
import org.jellyfin.mobile.music.navigation.MainTab
import org.jellyfin.mobile.music.navigation.MainTabs
import org.jellyfin.mobile.music.nowplaying.NowPlayingActions
import org.jellyfin.mobile.music.nowplaying.NowPlayingScreen
import org.jellyfin.mobile.music.nowplaying.NowPlayingUiState
import org.jellyfin.mobile.testsupport.PHONE_QUALIFIERS
import org.jellyfin.mobile.testsupport.SCREENSHOT_SDK
import org.jellyfin.mobile.testsupport.captureWithSystemBars
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** TuneVault's main screens on a phone with 3-button navigation; see [captureWithSystemBars]. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [SCREENSHOT_SDK], qualifiers = PHONE_QUALIFIERS)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TuneVaultScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String) {
        composeRule.waitForIdle()
        composeRule.activity.findViewById<View>(android.R.id.content).captureWithSystemBars(name)
    }

    @Test
    fun home() {
        val home = HomeUiState.Loaded(
            recentlyPlayed = listOf(
                album("damn", "Damn", artist = "Kendrick Lamar"),
                album("pb", "The Purple Bird", artist = "Bonnie \"Prince\" Billy"),
            ),
            recentlyAdded = listOf(
                album("hw", "Homework", artist = "Daft Punk"),
                album("blonde", "Blonde", artist = "Frank Ocean"),
            ),
            favoriteAlbums = listOf(album("pb", "The Purple Bird", artist = "Bonnie \"Prince\" Billy")),
            favoriteTracks = listOf(track("lm").copy(title = "London May"), track("om").copy(title = "Our Home")),
        )
        composeRule.setContent {
            AppTheme(VaultBrand.TuneVault) {
                // Laid out as the signed-in app arranges it: the selected tab above the bottom bar
                Column(Modifier.fillMaxSize()) {
                    MainTabs(
                        selected = MainTab.START,
                        onSelect = {},
                        home = { HomeScreen(state = home, onRetry = {}, onAlbumClick = {}, onShuffleAll = {}) },
                        library = {},
                        search = {},
                        modifier = Modifier.weight(1f),
                    )
                    MainBottomBar(selected = MainTab.START, onSelect = {})
                }
            }
        }
        capture("home")
    }

    @Test
    fun `now playing`() {
        val londonMay = track("lm").copy(
            title = "London May",
            artist = "Bonnie \"Prince\" Billy",
            albumTitle = "The Purple Bird",
        )
        val playing = NowPlayingUiState(
            track = londonMay,
            isPlaying = true,
            positionMs = 65_000L,
            durationMs = 180_000L,
        )
        composeRule.setContent {
            AppTheme(VaultBrand.TuneVault) { NowPlayingScreen(state = playing, actions = NowPlayingActions()) }
        }
        capture("now-playing")
    }
}
