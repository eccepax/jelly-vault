package org.jellyfin.mobile.livetv.playback

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import org.jellyfin.mobile.livetv.R
import org.jellyfin.mobile.livetv.channels.displayName
import org.jellyfin.mobile.livetv.domain.LiveChannel

/** What the player shows: [videoStarted] is the player actually rendering, not just the stream being open. */
data class WatchUiState(
    val playback: PlaybackState,
    val channel: LiveChannel?,
    val videoStarted: Boolean,
)

object PlayerScreenTags {
    const val BUFFERING = "player_buffering"
    const val VIDEO_PLAYING = "player_video_playing"
}

/**
 * Full-screen live TV: [video] underneath, and an overlay naming the channel that stays while
 * tuning or on a failure, and otherwise hides a few seconds after the last change or tap. The
 * remote's up/down (and channel keys) zap; on touch screens the overlay has buttons.
 */
@Composable
fun PlayerScreen(
    state: WatchUiState,
    onZap: (Int) -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    video: @Composable () -> Unit,
) {
    val focus = remember { FocusRequester() }
    var overlayRequests by remember { mutableIntStateOf(0) }
    var overlayHidden by remember { mutableIntStateOf(-1) }
    val playing = state.playback is PlaybackState.Playing && state.videoStarted
    LaunchedEffect(state.playback, state.videoStarted, overlayRequests) {
        if (playing) {
            delay(OVERLAY_TIMEOUT_MS)
            overlayHidden = overlayRequests
        }
    }
    val overlayVisible = !playing || overlayHidden != overlayRequests
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(if (playing) Modifier.testTag(PlayerScreenTags.VIDEO_PLAYING) else Modifier)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.ChannelUp -> onZap(+1)
                    Key.DirectionDown, Key.ChannelDown -> onZap(-1)
                    Key.DirectionCenter, Key.Enter, Key.DirectionLeft, Key.DirectionRight -> overlayRequests++
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { overlayRequests++ },
    ) {
        video()
        Center(state, onRetry, onExit)
        if (overlayVisible) Overlay(state.channel, onZap, onExit)
    }
}

@Composable
private fun Center(state: WatchUiState, onRetry: () -> Unit, onExit: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state.playback) {
            is PlaybackState.Tuning -> {
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.player_tuning),
                    color = Color.White,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            is PlaybackState.Playing -> if (!state.videoStarted) {
                CircularProgressIndicator(Modifier.testTag(PlayerScreenTags.BUFFERING))
            }
            is PlaybackState.Failed -> {
                Text(stringResource(R.string.player_failed), color = Color.White, style = MaterialTheme.typography.h6)
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    Button(onClick = onExit) { Text(stringResource(R.string.player_back)) }
                }
            }
            PlaybackState.Idle -> Unit
        }
    }
}

@Composable
private fun Overlay(channel: LiveChannel?, onZap: (Int) -> Unit, onExit: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = SCRIM_ALPHA), Color.Transparent)))
                .safeDrawingPadding()
                .padding(8.dp),
        ) {
            IconButton(onClick = onExit) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.player_back_to_channels),
                    tint = Color.White,
                )
            }
            if (channel != null) ChannelInfo(channel, Modifier.weight(1f))
            IconButton(onClick = { onZap(-1) }) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    stringResource(R.string.player_previous_channel),
                    tint = Color.White,
                )
            }
            IconButton(onClick = { onZap(+1) }) {
                Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.player_next_channel), tint = Color.White)
            }
        }
    }
}

@Composable
private fun ChannelInfo(channel: LiveChannel, modifier: Modifier) {
    Column(modifier.padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                channel.number,
                color = MaterialTheme.colors.primary,
                style = MaterialTheme.typography.subtitle2,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                channel.displayName,
                color = Color.White,
                style = MaterialTheme.typography.h6,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        channel.nowPlaying?.let { program ->
            Text(
                program.title,
                color = Color.White.copy(alpha = DIM_ALPHA),
                style = MaterialTheme.typography.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val OVERLAY_TIMEOUT_MS = 4_000L
private const val SCRIM_ALPHA = 0.7f
private const val DIM_ALPHA = 0.8f

/**
 * The player for [viewModel]'s channel: full screen, back returns to the channels. Leaving the app
 * frees the provider connection (and comes back to the channel on return); a rotation doesn't.
 */
@Composable
fun PlayerRoute(viewModel: WatchViewModel) {
    val state by viewModel.state.collectAsState()
    val activity = LocalActivity.current
    BackHandler(onBack = viewModel::exit)
    LifecycleStartEffect(viewModel) {
        viewModel.onForeground()
        onStopOrDispose {
            if (activity?.isChangingConfigurations != true) viewModel.onBackground()
        }
    }
    FullScreen()
    PlayerScreen(
        state = state,
        onZap = viewModel::zap,
        onRetry = viewModel::retry,
        onExit = viewModel::exit,
        video = {
            val exoPlayer = (viewModel.player as? ExoLivePlayer)?.exoPlayer
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        keepScreenOn = true
                        player = exoPlayer
                    }
                },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}

/** Hides the system bars while the player is up. */
@Composable
private fun FullScreen() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
