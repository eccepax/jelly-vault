package org.jellyfin.mobile.livetv.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.common.ui.components.SignOutFailures
import org.jellyfin.mobile.common.ui.startOver
import org.jellyfin.mobile.livetv.R
import org.jellyfin.mobile.livetv.account.AccountViewModel
import org.jellyfin.mobile.livetv.channels.CastStrip
import org.jellyfin.mobile.livetv.channels.ChannelsRoute
import org.jellyfin.mobile.livetv.channels.ChannelsViewModel
import org.jellyfin.mobile.livetv.domain.LiveChannel
import org.jellyfin.mobile.livetv.playback.PlaybackState
import org.jellyfin.mobile.livetv.playback.PlayerRoute
import org.jellyfin.mobile.livetv.playback.StreamCloser
import org.jellyfin.mobile.livetv.playback.WatchViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The signed-in app: the channel browser, or the player over it while a channel is being watched.
 * A channel left playing on a cast device shows along the bottom of the browser.
 */
@Composable
fun LiveSignedIn(
    watch: WatchViewModel = koinViewModel(),
    channels: ChannelsViewModel = koinViewModel(),
    account: AccountViewModel = koinViewModel(),
    closer: StreamCloser = koinInject(),
) {
    // Signed in, so the server can be reached: close whatever a killed previous run left open.
    LaunchedEffect(closer) { closer.closeLeftovers() }
    val watching by watch.watching.collectAsState()
    val signingOut by account.signingOut.collectAsState()
    val signedOut by account.signedOut.collectAsState()
    val activity = LocalActivity.current
    LaunchedEffect(signedOut) { if (signedOut) activity?.startOver() }
    SignOutFailures(account.signOutFailures)
    if (signingOut) {
        SigningOut()
    } else if (watching) {
        PlayerRoute(
            watch,
            onShowFree = {
                channels.showTag(LiveChannel.FREE_TAG)
                watch.exit()
            },
        )
    } else {
        val watchState by watch.state.collectAsState()
        ChannelsRoute(
            viewModel = channels,
            onChannelClick = watch::watch,
            onSignOut = account::signOut,
            bottomBar = {
                // The channel left playing on the cast device: back to it (its retry, if it failed there), or stop it
                val channel = watchState.channel
                if (watchState.casting && channel != null) {
                    CastStrip(
                        channel,
                        watchState.castDevice,
                        onOpen = watch::reopen,
                        onStop = watch::stopChannel,
                        failed = watchState.playback is PlaybackState.Failed,
                    )
                }
            },
        )
    }
}

@Composable
private fun SigningOut() {
    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(stringResource(R.string.signing_out), modifier = Modifier.padding(top = 12.dp))
        }
    }
}
