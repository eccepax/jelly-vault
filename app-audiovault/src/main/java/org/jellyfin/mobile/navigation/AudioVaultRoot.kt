package org.jellyfin.mobile.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.ui.VaultRoot
import org.jellyfin.mobile.common.ui.screens.connect.StyledTextButton
import org.jellyfin.mobile.common.ui.startOver
import org.jellyfin.mobile.downloads.AudiobookDownloads
import org.jellyfin.mobile.downloads.DownloadsRoute
import org.jellyfin.mobile.library.LibraryRoute
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.ui.PlayerRoute
import org.jellyfin.mobile.settings.SettingsRoute
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * AudioVault: connect to a server and sign in, then the library, with the player, downloads and
 * settings opened over it. The downloads can be opened before signing in, from the connect screen.
 */
@Composable
fun AudioVaultRoot(mainViewModel: MainViewModel, appPreferences: AppPreferences) {
    WithOfflineDownloads(
        app = { openDownloads ->
            VaultRoot(
                mainViewModel,
                appPreferences,
                logoRes = R.drawable.app_logo,
                connectActions = {
                    StyledTextButton(text = stringResource(R.string.view_downloads), onClick = openDownloads)
                },
            ) { SignedIn() }
        },
        downloads = { onBack -> Screens { _, play -> DownloadsRoute(onBack = onBack, onPlay = play) } },
    )
}

/** [app], or the [downloads] instead once [app] asks for them; their back returns. Saved across a rotation. */
@Composable
fun WithOfflineDownloads(
    app: @Composable (openDownloads: () -> Unit) -> Unit,
    downloads: @Composable (onBack: () -> Unit) -> Unit,
) {
    var showingDownloads by rememberSaveable { mutableStateOf(false) }
    if (showingDownloads) {
        // Composed before the downloads, so a page they open (the player) gets back first
        BackHandler { showingDownloads = false }
        downloads { showingDownloads = false }
    } else {
        app { showingDownloads = true }
    }
}

/** The library, kept whole: downloaded tracks whose files have gone are fetched again. */
@Composable
private fun SignedIn() {
    val downloads: AudiobookDownloads = koinInject()
    LaunchedEffect(Unit) { downloads.repair() }
    Screens { navigator, play ->
        LibraryRoute(onPlay = play, onOpenPlayer = navigator.openPlayer, onOpenSettings = navigator.openSettings)
    }
}

/** [home] with the pages it opens over it; `play` starts a book and opens the player on it. */
@Composable
private fun Screens(home: @Composable (navigator: Navigator, play: (PlayOptions) -> Unit) -> Unit) {
    val playerViewModel: AudiobookPlayerViewModel = koinViewModel()
    val activity = LocalActivity.current
    fun play(navigator: Navigator): (PlayOptions) -> Unit = { options ->
        playerViewModel.play(options)
        navigator.openPlayer()
    }
    AudioVaultNavigation(
        home = { navigator -> home(navigator, play(navigator)) },
        player = { onBack -> PlayerRoute(onBack = onBack, viewModel = playerViewModel) },
        downloads = { onBack, navigator -> DownloadsRoute(onBack = onBack, onPlay = play(navigator)) },
        settings = { onBack -> SettingsRoute(onBack = onBack, onSignedOut = { activity?.startOver() }) },
    )
}
