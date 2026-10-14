package org.jellyfin.mobile.livetv.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.jellyfin.mobile.livetv.channels.ChannelsRoute
import org.jellyfin.mobile.livetv.channels.ChannelsViewModel
import org.jellyfin.mobile.livetv.domain.LiveChannel
import org.jellyfin.mobile.livetv.playback.PlayerRoute
import org.jellyfin.mobile.livetv.playback.StreamCloser
import org.jellyfin.mobile.livetv.playback.WatchViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** The signed-in app: the channel browser, or the player over it while a channel is being watched. */
@Composable
fun LiveSignedIn(
    watch: WatchViewModel = koinViewModel(),
    channels: ChannelsViewModel = koinViewModel(),
    closer: StreamCloser = koinInject(),
) {
    // Signed in, so the server can be reached: close whatever a killed previous run left open.
    LaunchedEffect(closer) { closer.closeLeftovers() }
    val watching by watch.watching.collectAsState()
    if (watching) {
        PlayerRoute(
            watch,
            onShowFree = {
                channels.showTag(LiveChannel.FREE_TAG)
                watch.exit()
            },
        )
    } else {
        ChannelsRoute(viewModel = channels, onChannelClick = watch::watch)
    }
}
