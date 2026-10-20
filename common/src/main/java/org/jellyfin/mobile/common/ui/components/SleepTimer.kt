package org.jellyfin.mobile.common.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.playback.SleepTimerState
import java.util.Locale

private const val MINUTE_MS = 60_000L
private const val SECOND_MS = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60L

private const val QUARTER_HOUR = 15
private const val HALF_HOUR = 30
private const val THREE_QUARTERS = 45
private const val HOUR = 60

/** The sleep timer's choices, in minutes, with their labels. */
private val DURATIONS = listOf(
    QUARTER_HOUR to R.string.sleep_15,
    HALF_HOUR to R.string.sleep_30,
    THREE_QUARTERS to R.string.sleep_45,
    HOUR to R.string.sleep_60,
)

/** The sleep timer as a player screen shows and changes it. */
data class SleepTimerUi(
    val state: SleepTimerState = SleepTimerState.Off,
    val onStart: (durationMs: Long) -> Unit = {},
    val onEndOfSegment: () -> Unit = {},
    val onExtend: () -> Unit = {},
    val onCancel: () -> Unit = {},
)

/**
 * A moon that opens the timer's menu; lit while the timer runs. [endOfSegment] names the choice
 * to stop at the end of what's playing, in the app's terms ("End of this song").
 */
@Composable
fun SleepTimerButton(ui: SleepTimerUi, endOfSegment: String, modifier: Modifier = Modifier) {
    var menuOpen by remember { mutableStateOf(false) }
    fun choose(action: () -> Unit) {
        menuOpen = false
        action()
    }
    Box(modifier = modifier) {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                Icons.Filled.Bedtime,
                contentDescription = stringResource(R.string.sleep_timer),
                tint = if (ui.state == SleepTimerState.Off) LocalContentColor.current else MaterialTheme.colors.primary,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            if (ui.state is SleepTimerState.Counting) {
                DropdownMenuItem(onClick = { choose(ui.onExtend) }) { Text(stringResource(R.string.sleep_extend)) }
            } else {
                DURATIONS.forEach { (minutes, label) ->
                    DropdownMenuItem(
                        onClick = { choose { ui.onStart(minutes * MINUTE_MS) } },
                    ) { Text(stringResource(label)) }
                }
            }
            if (ui.state != SleepTimerState.EndOfSegment) {
                DropdownMenuItem(onClick = { choose(ui.onEndOfSegment) }) { Text(endOfSegment) }
            }
            if (ui.state != SleepTimerState.Off) {
                DropdownMenuItem(onClick = { choose(ui.onCancel) }) { Text(stringResource(R.string.sleep_off)) }
            }
        }
    }
}

/**
 * "Sleeps in 29:41", or [sleepsAfterSegment] ("Sleeps after this song") when stopping at the end
 * of what's playing; nothing while the timer is off.
 */
@Composable
fun SleepTimerLabel(state: SleepTimerState, sleepsAfterSegment: String, modifier: Modifier = Modifier) {
    val text = when (state) {
        SleepTimerState.Off -> return
        SleepTimerState.EndOfSegment -> sleepsAfterSegment
        is SleepTimerState.Counting -> stringResource(R.string.sleeps_in, formatRemaining(state.remainingMs))
    }
    Text(text, style = MaterialTheme.typography.caption, color = MaterialTheme.colors.primary, modifier = modifier)
}

/** Whole seconds left, rounded up, so it never shows 0:00 while still running. */
internal fun formatRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs + SECOND_MS - 1) / SECOND_MS
    val hours = totalSeconds / (SECONDS_PER_MINUTE * MINUTES_PER_HOUR)
    val minutes = totalSeconds / SECONDS_PER_MINUTE % MINUTES_PER_HOUR
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
