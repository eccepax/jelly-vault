package org.jellyfin.mobile.livetv.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Closes live streams on the server, and remembers the open ones in a [StreamLedger] that outlives
 * the app. A stream stays in the ledger until the server confirms the close, so one the app couldn't
 * close (offline, or the app was killed while watching) is closed on the next launch
 * ([closeLeftovers]). [scope] should outlive any screen: an app-wide scope.
 */
class StreamCloser(
    private val streams: LiveStreams,
    private val ledger: StreamLedger,
    private val scope: CoroutineScope,
) {
    /** Records a stream the server has just opened. */
    fun track(ticket: StreamTicket) {
        ledger.add(ticket)
    }

    /** Closes [ticket] in the background. */
    fun close(ticket: StreamTicket) {
        scope.launch { closeNow(ticket) }
    }

    /** Closes [ticket], retrying a few times; returns once it's closed or given up on. Never cancelled. */
    suspend fun closeNow(ticket: StreamTicket) = withContext(NonCancellable) {
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                streams.close(ticket)
                ledger.remove(ticket)
                return@withContext
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.w(e, "Closing live stream failed (attempt %d)", attempt + 1)
                delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        Timber.e("Gave up closing live stream %s; it stays in the ledger", ticket.liveStreamId)
    }

    private var leftoversClosed = false

    /**
     * Closes the streams a previous run left open. Only the first call does anything: after that
     * the ledger holds this run's streams, including the one being watched.
     */
    fun closeLeftovers() {
        if (leftoversClosed) return
        leftoversClosed = true
        val leftovers = ledger.all()
        if (leftovers.isEmpty()) return
        scope.launch { leftovers.forEach { closeNow(it) } }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val RETRY_DELAY_MS = 1_000L
    }
}
