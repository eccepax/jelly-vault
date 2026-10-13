package org.jellyfin.mobile.music.playback

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.koinInject

/** The id of the track that's playing (or paused), for highlighting it in lists. */
@Composable
fun currentTrackId(playback: PlaybackController = koinInject()): String? {
    val state by playback.state.collectAsState()
    return state.currentTrack?.id
}
