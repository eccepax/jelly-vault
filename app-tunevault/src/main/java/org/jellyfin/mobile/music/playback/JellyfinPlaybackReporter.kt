package org.jellyfin.mobile.music.playback

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
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

private const val TICKS_PER_MS = 10_000L

/**
 * [PlaybackReporter] over Jellyfin's session API. The server marks a track played (and bumps its
 * play count) when it's stopped near the end, and saves a resume position when stopped part way.
 */
class JellyfinPlaybackReporter(
    private val apiClient: ApiClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PlaybackReporter {

    override suspend fun started(trackId: String, positionMs: Long, isPaused: Boolean) = withContext(ioDispatcher) {
        apiClient.playStateApi.reportPlaybackStart(
            PlaybackStartInfo(
                itemId = trackId.toUUID(),
                positionTicks = positionMs * TICKS_PER_MS,
                isPaused = isPaused,
                canSeek = true,
                isMuted = false,
                playMethod = PlayMethod.DIRECT_STREAM,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
        Unit
    }

    override suspend fun progress(trackId: String, positionMs: Long, isPaused: Boolean) = withContext(ioDispatcher) {
        apiClient.playStateApi.reportPlaybackProgress(
            PlaybackProgressInfo(
                itemId = trackId.toUUID(),
                positionTicks = positionMs * TICKS_PER_MS,
                isPaused = isPaused,
                canSeek = true,
                isMuted = false,
                playMethod = PlayMethod.DIRECT_STREAM,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
        Unit
    }

    override suspend fun stopped(trackId: String, positionMs: Long) = withContext(ioDispatcher) {
        apiClient.playStateApi.reportPlaybackStopped(
            PlaybackStopInfo(itemId = trackId.toUUID(), positionTicks = positionMs * TICKS_PER_MS, failed = false),
        )
        Unit
    }

    override suspend fun playedAt(trackId: String, at: Instant) {
        withContext(ioDispatcher) {
            apiClient.playStateApi.markPlayedItem(
                trackId.toUUID(),
                datePlayed = LocalDateTime.ofInstant(at, ZoneOffset.UTC),
            )
        }
    }
}
