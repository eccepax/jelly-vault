package org.jellyfin.mobile.player.ui

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import org.jellyfin.mobile.R
import org.jellyfin.mobile.cast.CastButton
import org.jellyfin.mobile.cast.Casting
import org.jellyfin.mobile.cast.NoCasting
import org.jellyfin.mobile.common.playback.SleepTimerState
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.PlayerException
import org.jellyfin.mobile.player.PlayerPresence
import org.jellyfin.mobile.player.PlayerProgress
import org.jellyfin.mobile.player.playback.AudiobookPlaybackState
import org.jellyfin.mobile.utils.toast
import org.koin.compose.viewmodel.koinViewModel

/**
 * The book playing, in the playback service; this only shows it. [onBack] leaves: the back button,
 * the book stopping (the mini player's stop, the last track ending), or a book that couldn't be
 * started, with nothing else to show.
 */
@Composable
fun PlayerRoute(
    onBack: () -> Unit,
    viewModel: AudiobookPlayerViewModel = koinViewModel(),
    /** The app passes its own; without one there's no cast button. */
    casting: Casting = NoCasting,
) {
    val context = LocalContext.current
    val goBack by rememberUpdatedState(onBack)
    // Once: a book that can't start can stop the one playing too, and back can be tapped as the
    // book stops; each would leave
    var left by remember { mutableStateOf(false) }
    val leave = {
        if (!left) {
            left = true
            goBack()
        }
    }
    val playback by viewModel.state.collectAsState()
    val presence by viewModel.presence.collectAsState(viewModel.presenceNow)
    val speed by viewModel.speed.collectAsState()
    val sleepTimer by viewModel.sleepTimer.collectAsState()
    val skipSegment by viewModel.skipSegment.collectAsState()
    val castDevice by casting.castDevice.collectAsState()
    val progress = rememberProgress(viewModel)
    val holdToSpeedUp = rememberHoldToSpeedUp(viewModel)

    LeaveWhenStartFails(viewModel) { leave() }
    LeaveWhenStopped(presence) { leave() }
    ForgetStartFailuresOnLeaving(viewModel)
    ToastErrors(playback.error)
    // Not while casting: the phone may sit there for the whole book
    KeepScreenOn(playback.isPlayingHere)

    val playbackInfo = stringResource(
        // On a cast device, even a download is streamed
        if (playback.currentTrack?.fromDevice == true && !playback.isRemote) {
            R.string.playback_info_from_device
        } else {
            R.string.playback_info_streamed
        },
    )
    val state = playerUiState(playback, progress, playbackInfo).copy(
        speed = speed,
        holdToSpeedUp = holdToSpeedUp,
        sleepTimerActive = sleepTimer != SleepTimerState.Off,
        skipSegment = skipSegment != null,
    )
    PlayerScreen(
        state,
        PlayerActions(
            onBack = leave,
            onPlayPause = viewModel::togglePlayPause,
            onRewind = viewModel::rewind,
            onFastForward = viewModel::fastForward,
            onHoldSpeedUp = viewModel::holdSpeedUp,
            onPreviousChapter = viewModel::previousChapter,
            onNextChapter = viewModel::nextChapter,
            onSeek = viewModel::seekTo,
            onSpeed = viewModel::setSpeed,
            onSleepTimer = { choice -> chooseSleepTimer(context, viewModel, choice) },
            onTrack = viewModel::skipToTrack,
            onSkipSegment = viewModel::skipOfferedSegment,
        ),
        castDevice = castDevice,
        onStopCasting = casting::stopCasting,
        castButton = { CastButton(casting) },
    )
}

/** Where the book is, followed while the screen's in front: the player has no event for it moving. */
@Composable
private fun rememberProgress(viewModel: AudiobookPlayerViewModel): PlayerProgress {
    var progress by remember { mutableStateOf(PlayerProgress(positionMs = 0L, durationMs = 0L)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.progress.collect { progress = it }
        }
    }
    return progress
}

/** The "Hold to speed up" setting, read again each time the screen's back in front: it may have changed meanwhile. */
@Composable
private fun rememberHoldToSpeedUp(viewModel: AudiobookPlayerViewModel): Boolean {
    var holdToSpeedUp by remember(viewModel) { mutableStateOf(viewModel.holdToSpeedUp) }
    LifecycleResumeEffect(viewModel) {
        holdToSpeedUp = viewModel.holdToSpeedUp
        onPauseOrDispose {}
    }
    return holdToSpeedUp
}

/**
 * Says why a book couldn't be started, and leaves if there's nothing else to show. Heard only while
 * the screen's showing: one that failed with the app in the background leaves once it's back.
 */
@Composable
private fun LeaveWhenStartFails(viewModel: AudiobookPlayerViewModel, leave: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.startFailures.collect { failure ->
                context.toast(failure.messageRes)
                if (viewModel.state.value.currentTrack == null) leave()
            }
        }
    }
}

/**
 * Leaves once a book that was showing stops (saved, so it holds across a rotation), or with no book
 * and none starting: as it's restored once the app's process has gone, playback with it.
 */
@Composable
private fun LeaveWhenStopped(presence: PlayerPresence, leave: () -> Unit) {
    var hadBook by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(presence) {
        if (presence.hasBook) hadBook = true else if (hadBook || !presence.starting) leave()
    }
}

/** Once the screen's left (not just rotated), a book that couldn't start is no longer said to have. */
@Composable
private fun ForgetStartFailuresOnLeaving(viewModel: AudiobookPlayerViewModel) {
    val activity = LocalActivity.current
    DisposableEffect(viewModel) {
        onDispose { if (activity?.isChangingConfigurations != true) viewModel.forgetStartFailures() }
    }
}

/** Each new playback error, once: saved, so a rotation doesn't say it again. */
@Composable
private fun ToastErrors(error: String?) {
    val context = LocalContext.current
    var lastError by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(error) {
        if (error != null && error != lastError) context.toast(error)
        lastError = error
    }
}

@Composable
private fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

private val PlayerException.messageRes: Int
    get() = when (this) {
        is PlayerException.InvalidPlayOptions -> R.string.player_error_invalid_play_options
        is PlayerException.NetworkFailure -> R.string.player_error_network_failure
        is PlayerException.UnsupportedContent -> R.string.player_error_unsupported_content
        is PlayerException.StartTimedOut -> R.string.player_error_start_timed_out
    }

/** What the screen shows of [playback] and where it is; the rest comes from the view model as it is. */
internal fun playerUiState(
    playback: AudiobookPlaybackState,
    progress: PlayerProgress,
    playbackInfo: String,
): PlayerUiState {
    val track = playback.currentTrack ?: return PlayerUiState()
    val chapterStarts = if (track.durationMs > 0) {
        track.chapters.map { (it.startMs.toFloat() / track.durationMs).coerceIn(0f, 1f) }
    } else {
        emptyList()
    }
    return PlayerUiState(
        title = track.title,
        coverUrl = track.coverUrl,
        isPlaying = playback.playWhenReady,
        buffering = playback.isBuffering,
        positionMs = progress.positionMs.coerceAtLeast(0L),
        durationMs = progress.durationMs,
        chapterStarts = chapterStarts,
        // Chapters, or other tracks in the queue, to move between
        showChapterButtons = track.chapters.isNotEmpty() || playback.tracks.size > 1,
        tracks = playback.tracks.map { it.title },
        currentTrack = playback.currentIndex,
        playbackInfo = playbackInfo,
    )
}

private fun chooseSleepTimer(context: Context, viewModel: AudiobookPlayerViewModel, choice: SleepTimerChoice) {
    when (choice) {
        SleepTimerChoice.TurnOff -> {
            viewModel.cancelSleepTimer()
            context.toast(R.string.sleep_timer_off)
        }
        SleepTimerChoice.EndOfChapter -> {
            viewModel.sleepAtEndOfChapter()
            context.toast(R.string.sleep_timer_set_end_of_chapter)
        }
        is SleepTimerChoice.For -> {
            viewModel.startSleepTimer(choice.duration.minutes)
            val length = context.getString(
                when (choice.duration) {
                    SleepTimerDuration.FIFTEEN_MINUTES -> R.string.sleep_timer_15_min
                    SleepTimerDuration.THIRTY_MINUTES -> R.string.sleep_timer_30_min
                    SleepTimerDuration.FORTY_FIVE_MINUTES -> R.string.sleep_timer_45_min
                    SleepTimerDuration.SIXTY_MINUTES -> R.string.sleep_timer_60_min
                },
            )
            context.toast(context.getString(R.string.sleep_timer_set, length))
        }
    }
}
