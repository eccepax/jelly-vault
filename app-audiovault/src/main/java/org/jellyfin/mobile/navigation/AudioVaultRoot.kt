package org.jellyfin.mobile.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.ServerState
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.ui.VaultRoot
import org.jellyfin.mobile.common.ui.resolveRootScreen
import org.jellyfin.mobile.common.ui.screens.connect.StyledTextButton
import org.jellyfin.mobile.common.ui.startOver
import org.jellyfin.mobile.downloads.DownloadsRoute
import org.jellyfin.mobile.downloads.DownloadsViewModel
import org.jellyfin.mobile.library.LibraryRoute
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.ui.PlayerRoute
import org.jellyfin.mobile.settings.SettingsRoute
import org.koin.compose.viewmodel.koinViewModel

/**
 * AudioVault: connect to a server and sign in, then the library, with the player, downloads and
 * settings opened over it. The downloads can be opened before signing in too, from the connect
 * screen: one back stack either way, with the settings in it only while someone's signed in.
 */
@Composable
fun AudioVaultRoot(mainViewModel: MainViewModel, appPreferences: AppPreferences) {
    val playerViewModel: AudiobookPlayerViewModel = koinViewModel()
    val activity = LocalActivity.current
    // Worked out as VaultRoot does, which shows the matching screen at home
    val serverState by mainViewModel.serverState.collectAsState()
    val rootScreen = resolveRootScreen(serverState, appPreferences.currentUserId)
    val downloads: DownloadsViewModel = koinViewModel()
    RepairDownloadsOnceThereIsAServer(serverState, downloads::repairOnce)
    fun play(navigator: Navigator): (PlayOptions) -> Unit = { options ->
        playerViewModel.play(options)
        navigator.openPlayer()
    }
    AudioVaultNavigation(
        rootScreen = rootScreen,
        home = { navigator ->
            VaultRoot(
                mainViewModel,
                appPreferences,
                logoRes = R.drawable.app_logo,
                connectActions = {
                    StyledTextButton(text = stringResource(R.string.view_downloads), onClick = navigator.openDownloads)
                },
            ) {
                LibraryRoute(
                    onPlay = play(navigator),
                    onOpenPlayer = navigator.openPlayer,
                    onOpenSettings = navigator.openSettings,
                )
            }
        },
        player = { onBack -> PlayerRoute(onBack = onBack, viewModel = playerViewModel) },
        downloads = { onBack, navigator -> DownloadsRoute(onBack = onBack, onPlay = play(navigator)) },
        settings = { onBack -> SettingsRoute(onBack = onBack, onSignedOut = { activity?.startOver() }) },
    )
}

/**
 * Once there's a server, signed in or not (the downloads work signed out too), downloaded tracks
 * whose files have gone are fetched again: [repair] is asked each time a server's there anew.
 */
@Composable
internal fun RepairDownloadsOnceThereIsAServer(serverState: ServerState, repair: () -> Unit) {
    val available = serverState is ServerState.Available
    LaunchedEffect(available) { if (available) repair() }
}
