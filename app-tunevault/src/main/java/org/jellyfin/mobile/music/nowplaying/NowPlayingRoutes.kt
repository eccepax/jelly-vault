package org.jellyfin.mobile.music.nowplaying

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.jellyfin.mobile.music.navigation.PlayerScaffold
import org.jellyfin.mobile.music.playback.SleepTimer
import org.jellyfin.mobile.music.playback.SleepTimerVisibility
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** [PlayerScaffold] wired to the shared [NowPlayingViewModel]. */
@Composable
fun PlayerShell(
    viewModel: NowPlayingViewModel = koinViewModel(),
    bottomBar: (@Composable () -> Unit)? = null,
    library: @Composable () -> Unit,
) {
    val sleepTimer: SleepTimer = koinInject()
    val sleepTimerState by sleepTimer.state.collectAsState()
    val sleepTimerShown by koinInject<SleepTimerVisibility>().isShown.collectAsState()
    val state by viewModel.state.collectAsState()
    PlayerScaffold(
        hasTrack = state.track != null,
        library = library,
        bottomBar = bottomBar,
        miniPlayer = { onExpand ->
            MiniPlayer(
                state = state,
                onTogglePlayPause = viewModel::togglePlayPause,
                onNext = viewModel::skipToNext,
                onExpand = onExpand,
            )
        },
        nowPlaying = { onCollapse ->
            NowPlayingSheet(
                nowPlaying = { onOpenQueue ->
                    NowPlayingScreen(
                        state = state,
                        actions = NowPlayingActions(
                            onCollapse = onCollapse,
                            onTogglePlayPause = viewModel::togglePlayPause,
                            onNext = viewModel::skipToNext,
                            onPrevious = viewModel::skipToPrevious,
                            onSeek = viewModel::seekTo,
                            onCycleRepeat = viewModel::cycleRepeatMode,
                            onToggleShuffle = viewModel::toggleShuffle,
                            onOpenQueue = onOpenQueue,
                            onToggleFavorite = viewModel::toggleFavorite,
                        ),
                        sleepTimer = if (!sleepTimerShown) {
                            null
                        } else {
                            SleepTimerUi(
                                state = sleepTimerState,
                                onStart = sleepTimer::start,
                                onEndOfSong = sleepTimer::stopAtEndOfSong,
                                onExtend = { sleepTimer.extend() },
                                onCancel = sleepTimer::cancel,
                            )
                        },
                    )
                },
                queue = { onBack -> QueueRoute(onBack = onBack) },
            )
        },
    )
}
