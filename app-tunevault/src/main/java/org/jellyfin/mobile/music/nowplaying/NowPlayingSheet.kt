package org.jellyfin.mobile.music.nowplaying

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** The expanded player: Now Playing, or the queue opened from it. Back from the queue returns. */
@Composable
fun NowPlayingSheet(
    nowPlaying: @Composable (onOpenQueue: () -> Unit) -> Unit,
    queue: @Composable (onBack: () -> Unit) -> Unit,
) {
    var showQueue by rememberSaveable { mutableStateOf(false) }
    if (showQueue) {
        // Composed only while open, so it's the newest back callback and wins.
        BackHandler { showQueue = false }
        queue { showQueue = false }
    } else {
        nowPlaying { showQueue = true }
    }
}
