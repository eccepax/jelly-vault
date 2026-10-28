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
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import org.jellyfin.mobile.cast.CastButton
import org.jellyfin.mobile.cast.Casting
import org.jellyfin.mobile.cast.PlayingOnLabel
import org.jellyfin.mobile.common.ui.theme.VaultAlpha
import org.jellyfin.mobile.livetv.R
import org.jellyfin.mobile.livetv.channels.channelDisplayName
import org.jellyfin.mobile.livetv.channels.displayName
import org.jellyfin.mobile.livetv.domain.LiveChannel
import org.koin.compose.koinInject

/**
 * What the player shows: [videoStarted] is the player actually rendering, not just the stream being
 * open. [loginInUse] explains a failure on a channel that needs the provider login. [castDevice] is
 * where the channel plays while casting.
 */
data class WatchUiState(
    val playback: PlaybackState,
    val channel: LiveChannel?,
    val videoStarted: Boolean,
    val loginInUse: LoginInUse? = null,
    val castDevice: String? = null,
)

/** The provider login is probably taken; [holders] are the server's viewers known to have it. */
data class LoginInUse(val holders: List<ChannelSession>)

object PlayerScreenTags {
    const val BUFFERING = "player_buffering"
    const val VIDEO_PLAYING = "player_video_playing"
    const val FAILED_SMALL = "player_failed_small"
    const val CASTING = "player_casting"
}

/**
 * Full-screen live TV: [video] underneath, and an overlay naming the channel that stays while
 * tuning or on a failure, and otherwise hides a few seconds after the last change or tap. The
 * remote's up/down (and channel keys) zap; on touch screens the overlay has buttons. In the
 * picture-in-picture window, [inPictureInPicture], there's only the video (or a sign it failed).
 * While casting the overlay stays, over a panel naming the cast device ([onStopCasting] from there);
 * [castButton] is the app's cast button, in the overlay's bar.
 */
@Composable
fun PlayerScreen(
    state: WatchUiState,
    onZap: (Int) -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    onShowFree: () -> Unit,
    video: @Composable () -> Unit,
    inPictureInPicture: Boolean = false,
    onStopCasting: () -> Unit = {},
    castButton: @Composable () -> Unit = {},
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
    val casting = state.castDevice != null
    val overlayVisible = !playing || casting || overlayHidden != overlayRequests
    LaunchedEffect(Unit) { focus.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(if (playing) Modifier.testTag(PlayerScreenTags.VIDEO_PLAYING) else Modifier)
            .focusRequester(focus)
            .focusable()
            .then(
                if (inPictureInPicture) {
                    Modifier
                } else {
                    Modifier
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.DirectionUp, Key.ChannelUp -> onZap(+1)
                                Key.DirectionDown, Key.ChannelDown -> onZap(-1)
                                Key.DirectionCenter, Key.Enter, Key.DirectionLeft, Key.DirectionRight ->
                                    overlayRequests++
                                else -> return@onPreviewKeyEvent false
                            }
                            true
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { overlayRequests++ }
                },
            ),
    ) {
        // Always here, in or out of the floating window: moving it would make a new PlayerView,
        // re-attaching the player to a new surface with a black flash on every switch
        video()
        if (state.castDevice != null && !inPictureInPicture) CastingPanel(state.castDevice, onStopCasting)
        if (inPictureInPicture) {
            PictureInPictureContent(state)
        } else {
            Center(state, onRetry, onExit, onShowFree)
            if (overlayVisible) Overlay(state.channel, onZap, onExit, castButton)
        }
    }
}

/** The floating window is too small to read or tap: over the video, only a sign the channel failed. */
@Composable
private fun PictureInPictureContent(state: WatchUiState) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (state.playback is PlaybackState.Failed) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = stringResource(R.string.player_failed),
                tint = Color.White,
                modifier = Modifier.testTag(PlayerScreenTags.FAILED_SMALL),
            )
        }
    }
}

@Composable
private fun Center(state: WatchUiState, onRetry: () -> Unit, onExit: () -> Unit, onShowFree: () -> Unit) {
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
                val loginInUse = state.loginInUse
                if (loginInUse == null) {
                    Text(
                        stringResource(R.string.player_failed),
                        color = Color.White,
                        style = MaterialTheme.typography.h6,
                    )
                } else {
                    LoginInUseMessage(loginInUse)
                }
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (loginInUse != null) {
                        Button(onClick = onShowFree) { Text(stringResource(R.string.player_free_channels)) }
                    }
                    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    Button(onClick = onExit) { Text(stringResource(R.string.player_back)) }
                }
            }
            PlaybackState.Idle -> Unit
        }
    }
}

@Composable
private fun LoginInUseMessage(loginInUse: LoginInUse) {
    Text(
        stringResource(R.string.player_login_in_use),
        color = Color.White,
        style = MaterialTheme.typography.h6,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp),
    )
    Text(
        stringResource(R.string.player_login_in_use_detail),
        color = Color.White.copy(alpha = VaultAlpha.SECONDARY),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
    )
    loginInUse.holders.forEach { holder ->
        Text(
            stringResource(
                R.string.player_login_holder,
                holder.deviceName,
                holder.userName,
                channelDisplayName(holder.channelName),
            ),
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

/** In place of the video while the channel plays on [castDevice]: where it plays, and stopping that. */
@Composable
private fun CastingPanel(castDevice: String, onStopCasting: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag(PlayerScreenTags.CASTING),
    ) {
        Icon(
            Icons.Filled.CastConnected,
            contentDescription = null,
            tint = Color.White.copy(alpha = VaultAlpha.SECONDARY),
            modifier = Modifier.size(CAST_ICON_SIZE),
        )
        PlayingOnLabel(castDevice, onStopCasting, Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Overlay(
    channel: LiveChannel?,
    onZap: (Int) -> Unit,
    onExit: () -> Unit,
    castButton: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = VaultAlpha.SCRIM), Color.Transparent)),
                )
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
            castButton()
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
                color = Color.White.copy(alpha = VaultAlpha.SECONDARY),
                style = MaterialTheme.typography.body2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val OVERLAY_TIMEOUT_MS = 4_000L
private val CAST_ICON_SIZE = 64.dp

/**
 * The player for [viewModel]'s channel: full screen, back returns to the channels. Leaving the app
 * frees the provider connection (and comes back to the channel on return); a rotation doesn't.
 */
@Composable
fun PlayerRoute(viewModel: WatchViewModel, onShowFree: () -> Unit, casting: Casting = koinInject()) {
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
    // Leaving the app while a channel plays carries on in a floating window; on a cast device it
    // plays on regardless
    PictureInPictureOnLeave(enabled = state.playback is PlaybackState.Playing && state.castDevice == null)
    val inPictureInPicture = rememberInPictureInPicture()
    PlayerScreen(
        state = state,
        onZap = viewModel::zap,
        onRetry = viewModel::retry,
        onExit = viewModel::exit,
        onShowFree = onShowFree,
        inPictureInPicture = inPictureInPicture,
        onStopCasting = viewModel::stopCasting,
        castButton = { CastButton(casting) },
        video = {
            val exoPlayer = (viewModel.localPlayer as? ExoLivePlayer)?.exoPlayer
            // On only while the channel plays here: a cast plays on the TV, and the phone can sleep
            val playsHere = state.castDevice == null
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        player = exoPlayer
                    }
                },
                update = { it.keepScreenOn = playsHere },
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
