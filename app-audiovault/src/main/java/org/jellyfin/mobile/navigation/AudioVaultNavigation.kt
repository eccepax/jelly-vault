package org.jellyfin.mobile.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import org.jellyfin.mobile.common.ui.RootScreen
import org.jellyfin.mobile.common.ui.navigation.VaultBackStack
import org.jellyfin.mobile.common.ui.navigation.rememberVaultBackStack

private const val PLAYER = "player"
private const val DOWNLOADS = "downloads"
private const val SETTINGS = "settings"

/** What a screen can open on top of itself. */
class Navigator(
    val openPlayer: () -> Unit,
    val openDownloads: () -> Unit,
    val openSettings: () -> Unit,
)

/**
 * AudioVault's back stack ([VaultBackStack], as TuneVault's library has): [home] with the player,
 * downloads and settings opened on top of it, e.g. home → downloads → player. Back pops one level.
 *
 * The settings belong to whoever's signed in ([rootScreen], as home shows it): they wait while
 * that's still being read (restored after the app's process went), and close once there's no one.
 * The downloads, and the player on them, work signed out too.
 */
@Composable
fun AudioVaultNavigation(
    rootScreen: RootScreen,
    home: @Composable (navigator: Navigator) -> Unit,
    player: @Composable (onBack: () -> Unit) -> Unit,
    downloads: @Composable (onBack: () -> Unit, navigator: Navigator) -> Unit,
    settings: @Composable (onBack: () -> Unit) -> Unit,
) {
    val backStack = rememberVaultBackStack()
    val navigator = remember(backStack) {
        // Opening what's already showing (the mini player tapped twice) mustn't stack it again
        fun open(page: String) {
            if (backStack.pages.lastOrNull() != page) backStack.open(page)
        }
        Navigator(
            openPlayer = { open(PLAYER) },
            openDownloads = { open(DOWNLOADS) },
            openSettings = { open(SETTINGS) },
        )
    }

    val signedIn = rootScreen == RootScreen.SignedIn
    LaunchedEffect(rootScreen) {
        if (!signedIn && rootScreen != RootScreen.Splash) {
            val settingsAt = backStack.pages.indexOf(SETTINGS)
            if (settingsAt >= 0) backStack.closeFrom(settingsAt + 1)
        }
    }
    // What can show now: the pages under the settings, unless someone's signed in
    val showing = if (signedIn) backStack.pages.size else backStack.pages.takeWhile { it != SETTINGS }.size

    backStack.Show(depth = showing) { page, close ->
        when (page) {
            null -> home(navigator)
            PLAYER -> player(close)
            DOWNLOADS -> downloads(close, navigator)
            else -> settings(close)
        }
    }
}
