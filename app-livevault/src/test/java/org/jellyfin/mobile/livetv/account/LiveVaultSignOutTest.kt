package org.jellyfin.mobile.livetv.account

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.common.server.ServerSession
import org.jellyfin.mobile.livetv.playback.EventLog
import org.jellyfin.mobile.livetv.playback.FakeLiveStreams
import org.jellyfin.mobile.livetv.playback.FakeStreamLedger
import org.jellyfin.mobile.livetv.playback.StreamCloser
import org.jellyfin.mobile.livetv.playback.stream
import org.jellyfin.mobile.livetv.viewing.FakeViewingMemory
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveVaultSignOutTest {

    private val log = EventLog()
    private val streams = FakeLiveStreams(log)
    private val ledger = FakeStreamLedger()
    private val session = ServerSession { log += "end session" }
    private val memory = FakeViewingMemory(tag = "Sports", lastChannel = "id-43")

    @Test
    fun `open streams are closed while still signed in, then the session ends`() = runTest {
        val closer = StreamCloser(streams, ledger, scope = backgroundScope)
        closer.track(stream("a").ticket)

        liveVaultSignOut(closer, memory, session, logout = { log += "logout" })()

        log.events shouldContainExactly listOf("close:ls-a-1", "end session", "logout")
    }

    @Test
    fun `streams that could not be closed are forgotten, not left for the next account`() = runTest {
        val closer = StreamCloser(streams, ledger, scope = backgroundScope)
        closer.track(stream("a").ticket)
        streams.closeFailures = Int.MAX_VALUE

        liveVaultSignOut(closer, memory, session, logout = {})()

        ledger.all().shouldBeEmpty()
    }

    @Test
    fun `what was watched is forgotten`() = runTest {
        val closer = StreamCloser(streams, ledger, scope = backgroundScope)

        liveVaultSignOut(closer, memory, session, logout = {})()

        memory.lastChannelId.value shouldBe null
        memory.tag shouldBe null
    }
}
