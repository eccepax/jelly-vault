package org.jellyfin.mobile.player.playback

import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.jellyfin.sdk.model.serializer.toUUID
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Listening to downloaded tracks, kept with each download: the next play resumes from it, and
 * OfflineListeningSync tells the server later, as of when it happened.
 */
class DeviceAudiobookProgress(
    /** The track's download; null if it's gone. */
    private val download: suspend (itemId: UUID) -> LocalJellyfinMediaSource?,
    private val save: suspend (LocalJellyfinMediaSource) -> Unit,
    private val now: () -> Instant = Instant::now,
) : AudiobookProgressSink {

    /** Nothing to keep yet: where it starts is where it was left. */
    override suspend fun started(track: AudiobookTrack, positionMs: Long) = Unit

    override suspend fun progress(track: AudiobookTrack, positionMs: Long, isPaused: Boolean) =
        keep(track, positionMs.milliseconds, finished = false)

    override suspend fun stopped(track: AudiobookTrack, positionMs: Long) = keep(
        track,
        positionMs.milliseconds,
        finished = false,
    )

    /** Starts over next time, as the server does with a played track. */
    override suspend fun finished(track: AudiobookTrack) = keep(track, Duration.ZERO, finished = true)

    private suspend fun keep(track: AudiobookTrack, position: Duration, finished: Boolean) {
        val source = download(track.id.toUUID()) ?: return
        source.startTime = position
        source.positionSavedAt = now()
        source.finished = finished
        save(source)
    }
}
