package org.jellyfin.mobile.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.R
import org.jellyfin.mobile.cast.PlayingOnLabel
import org.jellyfin.mobile.common.playback.SleepTimerState
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.common.ui.components.AddBookmarkDialog
import org.jellyfin.mobile.common.ui.components.BookmarkUi
import org.jellyfin.mobile.common.ui.components.BookmarksDialog
import org.jellyfin.mobile.common.ui.components.Choice
import org.jellyfin.mobile.common.ui.components.ChoiceDialog
import org.jellyfin.mobile.common.ui.components.CoverArt
import org.jellyfin.mobile.common.ui.components.PlayPauseButton
import org.jellyfin.mobile.common.ui.components.SecondaryText
import org.jellyfin.mobile.common.ui.components.SleepTimerButton
import org.jellyfin.mobile.common.ui.components.SleepTimerLabel
import org.jellyfin.mobile.common.ui.components.SleepTimerUi
import org.jellyfin.mobile.common.ui.components.VaultTopBar
import org.jellyfin.mobile.common.ui.theme.VaultAlpha
import org.jellyfin.mobile.common.ui.theme.VaultSpacing
import java.util.Locale
import org.jellyfin.mobile.common.R as CommonR

/** Everything the player shows. */
data class PlayerUiState(
    val title: String = "",
    val coverUrl: String? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    /** 0 while the length isn't known yet. */
    val durationMs: Long = 0,
    /** Where each chapter starts, as a fraction of the whole. */
    val chapterStarts: List<Float> = emptyList(),
    /** Whether there are chapters, or other tracks, to move between. */
    val showChapterButtons: Boolean = false,
    val speed: Float = 1f,
    /** Whether holding fast-forward speeds playback up (the "Hold to speed up" setting). */
    val holdToSpeedUp: Boolean = true,
    val sleepTimer: SleepTimerState = SleepTimerState.Off,
    /** The queue's track names, and which is playing. */
    val tracks: List<String> = emptyList(),
    val currentTrack: Int = 0,
    val playbackInfo: String = "",
    /** Whether a skippable segment (an intro, an outro) is playing. */
    val skipSegment: Boolean = false,
    /** Once a book of a series has ended, the next one's title, offered to play; null otherwise. */
    val nextInSeries: String? = null,
    /** The book's bookmarks, the newest first. */
    val bookmarks: List<BookmarkUi> = emptyList(),
)

/** What the player can do. */
data class PlayerActions(
    val onBack: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onRewind: () -> Unit = {},
    val onFastForward: () -> Unit = {},
    /** Holding fast-forward started (true) or ended (false). */
    val onHoldSpeedUp: (Boolean) -> Unit = {},
    val onPreviousChapter: () -> Unit = {},
    val onNextChapter: () -> Unit = {},
    val onSeek: (Long) -> Unit = {},
    val onSpeed: (Float) -> Unit = {},
    val onStartSleepTimer: (durationMs: Long) -> Unit = {},
    val onSleepAtEndOfChapter: () -> Unit = {},
    val onExtendSleepTimer: () -> Unit = {},
    val onCancelSleepTimer: () -> Unit = {},
    val onTrack: (Int) -> Unit = {},
    val onSkipSegment: () -> Unit = {},
    val onPlayNextInSeries: () -> Unit = {},
    /** Bookmarks the place playing, with a note (empty for none). */
    val onAddBookmark: (note: String) -> Unit = {},
    val onOpenBookmark: (id: Long) -> Unit = {},
    val onDeleteBookmark: (id: Long) -> Unit = {},
    val onEditBookmarkNote: (id: Long, note: String) -> Unit = { _, _ -> },
)

/** Test tags for the player's parts that are read rather than tapped. */
object PlayerTags {
    const val POSITION = "player_position"
    const val DURATION = "player_duration"
    const val SEEK_BAR = "player_seek_bar"
    const val PLAY_PAUSE = "player_play_pause"
}

/**
 * [castDevice] names the cast device the book is playing on (null while it's on this device), and
 * tapping its name offers [onStopCasting]; [connectingTo] names one picked and still starting.
 * [castButton] is the app's cast button.
 */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    actions: PlayerActions,
    castDevice: String? = null,
    connectingTo: String? = null,
    onStopCasting: () -> Unit = {},
    castButton: @Composable () -> Unit = {},
) {
    var showingInfo by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<PlayerDialog?>(null) }

    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.systemBarsPadding()) {
            PlayerTopBar(
                state = state,
                onBack = actions.onBack,
                onDialog = { dialog = it },
                onPlaybackInfo = { showingInfo = !showingInfo },
                castButton = castButton,
                sleepTimer = sleepTimerUi(state, actions),
            )
            CoverArea(
                state = state,
                showingInfo = showingInfo,
                onHideInfo = { showingInfo = false },
                onSkipSegment = actions.onSkipSegment,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = state.title,
                style = MaterialTheme.typography.h6,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VaultSpacing.XLarge, vertical = VaultSpacing.Large)
                    .basicMarquee(),
            )
            state.nextInSeries?.let { NextInSeries(it, actions.onPlayNextInSeries) }
            ChapterSeekBar(state, actions.onSeek)
            PlayerControls(state, actions)
            SleepTimerLabel(
                state.sleepTimer,
                stringResource(R.string.sleeps_after_chapter),
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = VaultSpacing.Small),
            )
            PlayingOnLabel(
                castDevice,
                onStopCasting,
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = VaultSpacing.Small),
                connectingTo = connectingTo,
            )
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = VaultSpacing.Large),
                contentAlignment = Alignment.Center,
            ) {
                OutlinedButton(onClick = { dialog = PlayerDialog.SPEED }) { Text(speedLabel(state.speed)) }
            }
        }
    }

    dialog?.let { PlayerDialogs(it, state, actions) { dialog = null } }
}

/** The top bar's "more" menu: the tracklist (with more than one track), bookmarks, playback info. */
@Composable
private fun PlayerMenu(showTracklist: Boolean, onDialog: (PlayerDialog) -> Unit, onPlaybackInfo: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }

    @Composable
    fun item(label: String, onClick: () -> Unit) = DropdownMenuItem(
        onClick = {
            menuOpen = false
            onClick()
        },
    ) { Text(label) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(CommonR.string.vault_more_options))
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (showTracklist) item(stringResource(R.string.player_tracklist)) { onDialog(PlayerDialog.TRACKS) }
            item(stringResource(CommonR.string.add_bookmark_action)) { onDialog(PlayerDialog.ADD_BOOKMARK) }
            item(stringResource(CommonR.string.bookmarks_title)) { onDialog(PlayerDialog.BOOKMARKS) }
            item(stringResource(R.string.menu_playback_info), onPlaybackInfo)
        }
    }
}

/** The sleep timer as its button shows and changes it. */
private fun sleepTimerUi(state: PlayerUiState, actions: PlayerActions) = SleepTimerUi(
    state = state.sleepTimer,
    onStart = actions.onStartSleepTimer,
    onEndOfSegment = actions.onSleepAtEndOfChapter,
    onExtend = actions.onExtendSleepTimer,
    onCancel = actions.onCancelSleepTimer,
)

/** The player's dialogs, one open at a time. */
private enum class PlayerDialog { SPEED, TRACKS, ADD_BOOKMARK, BOOKMARKS }

@Composable
private fun PlayerDialogs(dialog: PlayerDialog, state: PlayerUiState, actions: PlayerActions, onDismiss: () -> Unit) {
    when (dialog) {
        PlayerDialog.SPEED -> SpeedDialog(state.speed, actions.onSpeed, onDismiss)
        PlayerDialog.TRACKS -> TracklistDialog(state, actions.onTrack, onDismiss)
        PlayerDialog.ADD_BOOKMARK -> AddBookmarkDialog(onSave = actions.onAddBookmark, onDismiss = onDismiss)
        PlayerDialog.BOOKMARKS -> BookmarksDialog(
            bookmarks = state.bookmarks,
            onOpen = actions.onOpenBookmark,
            onDelete = actions.onDeleteBookmark,
            onDismiss = onDismiss,
            onEditNote = actions.onEditBookmarkNote,
        )
    }
}

/** The next book of the series the one that ended is in, and a button to play it. */
@Composable
private fun NextInSeries(title: String, onPlay: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.XLarge),
    ) {
        Text(
            text = stringResource(R.string.next_in_series, title),
            style = MaterialTheme.typography.body2,
            maxLines = 2,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onPlay) { Text(stringResource(R.string.play_next_in_series)) }
    }
}

/** The cover, with what goes over it: buffering progress, playback info, and the skip button. */
@Composable
private fun CoverArea(
    state: PlayerUiState,
    showingInfo: Boolean,
    onHideInfo: () -> Unit,
    onSkipSegment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().padding(horizontal = VaultSpacing.XLarge)) {
        CoverArt(
            url = state.coverUrl,
            placeholder = Icons.Filled.Headphones,
            modifier = Modifier.align(Alignment.Center),
        )
        if (state.buffering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).testTag(VaultTestTags.LOADING))
        }
        if (showingInfo) PlaybackInfo(state.playbackInfo, onDismiss = onHideInfo)
        if (state.skipSegment) {
            OutlinedButton(
                onClick = onSkipSegment,
                modifier = Modifier.align(Alignment.BottomEnd).padding(VaultSpacing.Large),
            ) { Text(stringResource(R.string.skip_media_segment)) }
        }
    }
}

@Composable
private fun SpeedDialog(speed: Float, onSpeed: (Float) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(
        title = stringResource(R.string.menu_speed),
        choices = SPEEDS.map { Choice(it, speedLabel(it)) },
        selected = SPEEDS.firstOrNull { kotlin.math.abs(it - speed) < SPEED_EPSILON },
        onSelect = onSpeed,
        onDismiss = onDismiss,
    )
}

/** The queue's tracks, marking the one playing. */
@Composable
private fun TracklistDialog(state: PlayerUiState, onTrack: (Int) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(
        title = stringResource(R.string.player_tracklist),
        choices = state.tracks.mapIndexed { index, name -> Choice(index, name) },
        selected = state.currentTrack,
        onSelect = onTrack,
        onDismiss = onDismiss,
    )
}

@Composable
private fun PlayerTopBar(
    state: PlayerUiState,
    onBack: () -> Unit,
    onDialog: (PlayerDialog) -> Unit,
    onPlaybackInfo: () -> Unit,
    castButton: @Composable () -> Unit,
    sleepTimer: SleepTimerUi,
) {
    VaultTopBar(
        title = "",
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(CommonR.string.vault_back),
                )
            }
        },
        actions = {
            castButton()
            SleepTimerButton(sleepTimer, stringResource(R.string.sleep_end_of_chapter))
            PlayerMenu(showTracklist = state.tracks.size > 1, onDialog = onDialog, onPlaybackInfo = onPlaybackInfo)
        },
    )
}

/** Tapping it, like the old panel, hides it. */
@Composable
private fun PlaybackInfo(info: String, onDismiss: () -> Unit) {
    Text(
        text = info,
        color = Color.White,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = VaultAlpha.SCRIM))
            .clickable(onClick = onDismiss)
            .padding(VaultSpacing.Large),
    )
}

/**
 * The seek bar, with a mark where each chapter starts: in the accent once played, dim before. It
 * follows the playback position except while being dragged, and seeks when let go.
 */
@Composable
private fun ChapterSeekBar(state: PlayerUiState, onSeek: (Long) -> Unit) {
    val duration = state.durationMs.coerceAtLeast(0)
    val playedFraction = if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    var dragging by remember { mutableStateOf<Float?>(null) }
    val currentOnSeek by rememberUpdatedState(onSeek)
    val shown = dragging ?: playedFraction
    val played = MaterialTheme.colors.primary
    val unplayed = MaterialTheme.colors.onSurface.copy(alpha = VaultAlpha.DIVIDER * 3)
    Column(modifier = Modifier.padding(horizontal = VaultSpacing.Large)) {
        Box(contentAlignment = Alignment.Center) {
            Slider(
                value = shown,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { currentOnSeek((it * duration).toLong()) }
                    dragging = null
                },
                enabled = duration > 0,
                colors = SliderDefaults.colors(inactiveTrackColor = unplayed),
                modifier = Modifier.fillMaxWidth().testTag(PlayerTags.SEEK_BAR),
            )
            if (state.chapterStarts.isNotEmpty()) {
                // Inset to the slider's track, which leaves room for its thumb at either end
                Canvas(modifier = Modifier.fillMaxWidth().height(MARKER_HEIGHT).padding(horizontal = THUMB_RADIUS)) {
                    state.chapterStarts.forEach { start ->
                        val x = size.width * start
                        drawLine(
                            color = if (start <= shown) played else unplayed,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = MARKER_WIDTH.toPx(),
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = VaultSpacing.Small)) {
            SecondaryText(
                formatTime(if (dragging != null) (shown * duration).toLong() else state.positionMs),
                style = MaterialTheme.typography.body2,
                modifier = Modifier.testTag(PlayerTags.POSITION),
            )
            Box(modifier = Modifier.weight(1f))
            SecondaryText(
                if (duration > 0) formatTime(duration) else UNKNOWN_DURATION,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.testTag(PlayerTags.DURATION),
            )
        }
    }
}

@Composable
private fun PlayerControls(state: PlayerUiState, actions: PlayerActions) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = VaultSpacing.Large),
    ) {
        if (state.showChapterButtons) {
            IconButton(onClick = actions.onPreviousChapter) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.player_previous_chapter))
            }
        }
        IconButton(onClick = actions.onRewind) {
            Icon(
                Icons.Filled.Replay10,
                contentDescription = stringResource(R.string.player_rewind),
                modifier = Modifier.size(SKIP_ICON_SIZE),
            )
        }
        Surface(shape = CircleShape, color = MaterialTheme.colors.primary) {
            PlayPauseButton(
                isPlaying = state.isPlaying,
                onClick = actions.onPlayPause,
                iconSize = PLAY_ICON_SIZE,
                modifier = Modifier.size(PLAY_SIZE).testTag(PlayerTags.PLAY_PAUSE),
            )
        }
        FastForwardButton(state.holdToSpeedUp, actions)
        if (state.showChapterButtons) {
            IconButton(onClick = actions.onNextChapter) {
                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.player_next_chapter))
            }
        }
    }
}

/**
 * A tap skips forward; holding plays faster until let go, when [holdToSpeedUp] is on (otherwise a
 * hold is just a tap). A hold doesn't also skip.
 */
@Composable
private fun FastForwardButton(holdToSpeedUp: Boolean, actions: PlayerActions) {
    val label = stringResource(R.string.player_fast_forward)
    val currentActions by rememberUpdatedState(actions)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(BUTTON_SIZE)
            .semantics {
                contentDescription = label
                role = Role.Button
                onClick {
                    currentActions.onFastForward()
                    true
                }
            }
            .pointerInput(holdToSpeedUp) {
                var holding = false
                detectTapGestures(
                    onTap = { currentActions.onFastForward() },
                    onLongPress = {
                        if (holdToSpeedUp) {
                            holding = true
                            currentActions.onHoldSpeedUp(true)
                        } else {
                            currentActions.onFastForward()
                        }
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (holding) {
                            holding = false
                            currentActions.onHoldSpeedUp(false)
                        }
                    },
                )
            },
    ) {
        Icon(Icons.Filled.Forward30, contentDescription = null, modifier = Modifier.size(SKIP_ICON_SIZE))
    }
}

private fun speedLabel(speed: Float) = "${speed}x"

/** A position or length as h:mm:ss, or m:ss under an hour; part seconds are dropped. */
internal fun formatTime(timeMs: Long): String {
    val totalSeconds = (timeMs / MILLISECONDS_PER_SECOND).toInt()
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

@Suppress("MagicNumber")
private val SPEEDS = (2..8).map { it * 0.25f }
private const val SPEED_EPSILON = 0.01f
private const val UNKNOWN_DURATION = "--:--"
private const val MILLISECONDS_PER_SECOND = 1000
private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_MINUTE = 60
private const val PLAY_ICON_SIZE = 40
private val PLAY_SIZE = 72.dp
private val BUTTON_SIZE = 56.dp
private val SKIP_ICON_SIZE = 36.dp
private val MARKER_HEIGHT = 12.dp
private val MARKER_WIDTH = 2.dp
private val THUMB_RADIUS = 10.dp
