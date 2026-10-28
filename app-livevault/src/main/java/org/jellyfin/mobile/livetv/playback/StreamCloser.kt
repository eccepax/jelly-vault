package org.jellyfin.mobile.livetv.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Closes live streams on the server, and remembers the open ones in a [StreamLedger] that outlives
 * the app. A stream stays in the ledger until the server confirms the close, so one the app couldn't
 * close (offline, or the app was killed while watching) is closed on the next launch
 * ([closeLeftovers]). [scope] should outlive any screen: an app-wide scope.
 *
 * Each stream is closed once at a time: every close takes one viewer off the server's shared tuner
 * stream, so closing the same open twice would cut off someone else watching the channel.
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
        closing(ticket)
    }

    /**
     * Closes [ticket], retrying a few times; returns once it's closed or given up on. Never
     * cancelled. Joins a close of the same ticket that's already under way.
     */
    suspend fun closeNow(ticket: StreamTicket) = withContext(NonCancellable) { closing(ticket).await() }

    /** Closes every stream still open (signing out): returns once each is closed or given up on. */
    suspend fun closeAll() = withContext(NonCancellable) { ledger.all().map(::closing).awaitAll() }

    /** Forgets the streams that couldn't be closed: the next account can't close them. */
    fun forgetAll() {
        ledger.all().forEach(ledger::remove)
    }

    private val inFlight = mutableMapOf<StreamTicket, Deferred<Unit>>()

    /**
     * The close of [ticket]: the one under way, or a new one. Signing out stops the channel being
     * watched before closing the rest (see WatchingNow), so nothing closes a ticket after it.
     */
    private fun closing(ticket: StreamTicket): Deferred<Unit> = synchronized(inFlight) {
        inFlight.getOrPut(ticket) {
            scope.async {
                try {
                    closeWithRetries(ticket)
                } finally {
                    synchronized(inFlight) { inFlight.remove(ticket) }
                }
            }
        }
    }

    private suspend fun closeWithRetries(ticket: StreamTicket) {
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                streams.close(ticket)
                ledger.remove(ticket)
                return
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
        leftovers.forEach(::closing)
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
        const val RETRY_DELAY_MS = 1_000L
    }
}
