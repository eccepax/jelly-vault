package org.jellyfin.mobile.player.playback

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.serializer.toUUID

private const val TICKS_PER_MS = 10_000L

/**
 * Listening told to the server through Jellyfin's session API: it keeps the resume position from
 * each report, and a track stopped at its end is played (next time it starts over).
 */
class JellyfinAudiobookProgress(
    private val apiClient: ApiClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AudiobookProgressSink {

    override suspend fun started(track: AudiobookTrack, positionMs: Long) = withContext(ioDispatcher) {
        apiClient.playStateApi.reportPlaybackStart(
            PlaybackStartInfo(
                itemId = track.id.toUUID(),
                positionTicks = positionMs * TICKS_PER_MS,
                isPaused = false,
                canSeek = true,
                isMuted = false,
                playMethod = PlayMethod.DIRECT_PLAY,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
        Unit
    }

    override suspend fun progress(
        track: AudiobookTrack,
        positionMs: Long,
        isPaused: Boolean,
    ) = withContext(ioDispatcher) {
        apiClient.playStateApi.reportPlaybackProgress(
            PlaybackProgressInfo(
                itemId = track.id.toUUID(),
                positionTicks = positionMs * TICKS_PER_MS,
                isPaused = isPaused,
                canSeek = true,
                isMuted = false,
                playMethod = PlayMethod.DIRECT_PLAY,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
        Unit
    }

    override suspend fun stopped(track: AudiobookTrack, positionMs: Long) = withContext(ioDispatcher) {
        apiClient.playStateApi.reportPlaybackStopped(
            PlaybackStopInfo(itemId = track.id.toUUID(), positionTicks = positionMs * TICKS_PER_MS, failed = false),
        )
        Unit
    }

    override suspend fun finished(track: AudiobookTrack) = withContext(ioDispatcher) {
        apiClient.playStateApi.reportPlaybackStopped(
            PlaybackStopInfo(
                itemId = track.id.toUUID(),
                positionTicks = track.durationMs * TICKS_PER_MS,
                failed = false,
            ),
        )
        apiClient.playStateApi.markPlayedItem(track.id.toUUID())
        Unit
    }
}
