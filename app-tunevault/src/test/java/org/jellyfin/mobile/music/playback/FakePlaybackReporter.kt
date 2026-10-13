package org.jellyfin.mobile.music.playback

import kotlinx.coroutines.awaitCancellation
import java.io.IOException
import java.time.Instant

/** Records reports as readable strings; can be told to fail. */
class FakePlaybackReporter : PlaybackReporter {
    val reports = mutableListOf<String>()
    var failNext = false

    /** Never answer, like a server that has gone away. */
    var hang = false

    /** Fail every report with this, until cleared. */
    var failure: Exception? = null

    private suspend fun record(report: String) {
        if (hang) awaitCancellation()
        failure?.let { throw it }
        if (failNext) {
            failNext = false
            throw IOException("offline")
        }
        reports += report
    }

    override suspend fun started(trackId: String, positionMs: Long, isPaused: Boolean) =
        record("started $trackId @${positionMs / 1000}s${if (isPaused) " paused" else ""}")

    override suspend fun progress(trackId: String, positionMs: Long, isPaused: Boolean) =
        record("progress $trackId @${positionMs / 1000}s${if (isPaused) " paused" else ""}")

    override suspend fun stopped(trackId: String, positionMs: Long) = record("stopped $trackId @${positionMs / 1000}s")

    override suspend fun playedAt(trackId: String, at: Instant) = record("playedAt $trackId $at")
}
