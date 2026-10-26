package org.jellyfin.mobile.music.nowplaying

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.jellyfin.mobile.cast.CastButton
import org.jellyfin.mobile.cast.Casting
import org.jellyfin.mobile.common.playback.SleepTimer
import org.jellyfin.mobile.common.ui.components.SleepTimerUi
import org.jellyfin.mobile.music.navigation.PlayerScaffold
import org.jellyfin.mobile.music.playback.SleepTimerVisibility
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** [PlayerScaffold] wired to the shared [NowPlayingViewModel]. */
@Composable
fun PlayerShell(
    viewModel: NowPlayingViewModel = koinViewModel(),
    bottomBar: (@Composable () -> Unit)? = null,
    /** Open an album, or an artist's page, in the library (the player closes first). */
    onOpenAlbum: (albumId: String) -> Unit = {},
    onOpenArtist: (artistId: String) -> Unit = {},
    library: @Composable () -> Unit,
) {
    val sleepTimer = sleepTimerUi()
    val state by viewModel.state.collectAsState()
    val casting: Casting = koinInject()
    val castDevice by casting.castDevice.collectAsState()
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
                            onOpenAlbum = { albumId ->
                                onCollapse()
                                onOpenAlbum(albumId)
                            },
                            onOpenArtist = { artistId ->
                                onCollapse()
                                onOpenArtist(artistId)
                            },
                        ),
                        sleepTimer = sleepTimer,
                        castDevice = castDevice,
                        castButton = { CastButton(casting) },
                    )
                },
                queue = { onBack -> QueueRoute(onBack = onBack) },
            )
        },
    )
}

/** The sleep timer's controls, or null while the timer is hidden (a setting). */
@Composable
private fun sleepTimerUi(): SleepTimerUi? {
    val sleepTimer: SleepTimer = koinInject()
    val sleepTimerState by sleepTimer.state.collectAsState()
    val sleepTimerShown by koinInject<SleepTimerVisibility>().isShown.collectAsState()
    if (!sleepTimerShown) return null
    return SleepTimerUi(
        state = sleepTimerState,
        onStart = sleepTimer::start,
        onEndOfSegment = sleepTimer::stopAtEndOfSegment,
        onExtend = { sleepTimer.extend() },
        onCancel = sleepTimer::cancel,
    )
}
