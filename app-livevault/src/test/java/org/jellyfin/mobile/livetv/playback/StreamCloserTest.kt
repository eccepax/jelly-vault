package org.jellyfin.mobile.livetv.playback

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
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

    @Test
    fun `closing everything closes every open stream`() = runTest {
        val closer = closer()
        val a = stream("a").ticket
        val b = stream("b").ticket
        closer.track(a)
        closer.track(b)

        closer.closeAll()

        log.events shouldContainExactlyInAnyOrder listOf("close:ls-a-1", "close:ls-b-1")
        ledger.all().shouldBeEmpty()
    }

    @Test
    fun `a stream already closing is not closed a second time`() = runTest {
        // Each close takes one viewer off the server's shared stream: a second would cut off another viewer.
        val closer = closer()
        val ticket = stream("a").ticket
        closer.track(ticket)
        val gate = CompletableDeferred<Unit>()
        streams.closeGate = gate
        closer.close(ticket)
        runCurrent()

        val closingAll = backgroundScope.launch { closer.closeAll() }
        runCurrent()
        gate.complete(Unit)
        settle()

        closingAll.isCompleted shouldBe true
        log.events shouldContainExactly listOf("close:ls-a-1")
    }

    @Test
    fun `a stream already closed is not closed again`() = runTest {
        // Signing out closes everything, then the screen still holding the stream lets go of it too
        val closer = closer()
        val ticket = stream("a").ticket
        closer.track(ticket)
        closer.closeAll()

        closer.closeNow(ticket)
        closer.close(ticket)
        settle()

        log.events shouldContainExactly listOf("close:ls-a-1")
    }

    @Test
    fun `closing everything waits for a close already under way`() = runTest {
        val closer = closer()
        val ticket = stream("a").ticket
        closer.track(ticket)
        val gate = CompletableDeferred<Unit>()
        streams.closeGate = gate
        closer.close(ticket)
        runCurrent()

        val closingAll = backgroundScope.launch { closer.closeAll() }
        runCurrent()

        closingAll.isCompleted shouldBe false
        gate.complete(Unit)
        settle()
        closingAll.isCompleted shouldBe true
    }

    @Test
    fun `forgetting drops the streams that could not be closed`() = runTest {
        val closer = closer()
        closer.track(stream("a").ticket)

        closer.forgetAll()

        ledger.all().shouldBeEmpty()
    }
}
