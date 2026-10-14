package org.jellyfin.mobile.livetv.playback

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamCloserTest {

    private val log = EventLog()
    private val streams = FakeLiveStreams(log)
    private val ledger = FakeStreamLedger()

    private fun TestScope.closer() = StreamCloser(streams, ledger, scope = backgroundScope)

    @Test
    fun `an open stream is in the ledger until it is closed`() = runTest {
        val closer = closer()
        val ticket = stream("a").ticket

        closer.track(ticket)
        ledger.all() shouldContainExactly listOf(ticket)

        closer.close(ticket)
        settle()

        log.events shouldContainExactly listOf("close:ls-a-1")
        ledger.all().shouldBeEmpty()
    }

    @Test
    fun `a failed close is retried until it goes through`() = runTest {
        val closer = closer()
        val ticket = stream("a").ticket
        closer.track(ticket)
        streams.closeFailures = 2

        closer.close(ticket)
        settle()

        log.events shouldContainExactly listOf("close-failed:ls-a-1", "close-failed:ls-a-1", "close:ls-a-1")
        ledger.all().shouldBeEmpty()
    }

    @Test
    fun `a close that keeps failing stays in the ledger for next time`() = runTest {
        val closer = closer()
        val ticket = stream("a").ticket
        closer.track(ticket)
        streams.closeFailures = Int.MAX_VALUE

        closer.close(ticket)
        settle()

        ledger.all() shouldContainExactly listOf(ticket)
    }

    @Test
    fun `streams left open last time are closed`() = runTest {
        // The app was killed while watching: the ledger outlived it.
        ledger.add(stream("a").ticket)
        ledger.add(stream("b").ticket)

        closer().closeLeftovers()
        settle()

        log.events shouldContainExactly listOf("close:ls-a-1", "close:ls-b-1")
        ledger.all().shouldBeEmpty()
    }

    @Test
    fun `leftovers are only closed once per run`() = runTest {
        // Later, the ledger holds this run's streams: the one being watched must not be closed
        // because the screen asking was recreated (a rotation).
        val closer = closer()
        closer.closeLeftovers()
        settle()
        val watching = stream("now").ticket
        closer.track(watching)

        closer.closeLeftovers()
        settle()

        log.events.shouldBeEmpty()
        ledger.all() shouldContainExactly listOf(watching)
    }
}
