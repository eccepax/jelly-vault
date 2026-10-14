package org.jellyfin.mobile.livetv.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

/** Everything the fakes did, in order, so tests can check that a stream closes before the next opens. */
class EventLog {
    val events = mutableListOf<String>()
    operator fun plusAssign(event: String) {
        events += event
    }
}

fun stream(channelId: String, n: Int = 1) = OpenedStream(
    channelId = channelId,
    liveStreamId = "ls-$channelId-$n",
    playSessionId = "ps-$channelId-$n",
    mediaSourceId = "ms-$channelId",
    url = "http://vault/$channelId/$n.m3u8",
)

class FakeLiveStreams(private val log: EventLog) : LiveStreams {
    private val opens = mutableMapOf<String, Int>()

    /** Channels whose open fails. */
    val failing = mutableSetOf<String>()

    /** Opens of these channels wait for the gate (the server takes seconds to tune). */
    val gates = mutableMapOf<String, CompletableDeferred<Unit>>()

    /** Closes fail this many more times before succeeding. */
    var closeFailures = 0

    /** Closes wait for this gate (the server takes a moment). */
    var closeGate: CompletableDeferred<Unit>? = null

    val openStreams = mutableSetOf<String>()

    override suspend fun open(channelId: String): OpenedStream {
        log += "open:$channelId"
        gates[channelId]?.await()
        if (channelId in failing) throw java.io.IOException("tuner busy")
        val n = (opens[channelId] ?: 0) + 1
        opens[channelId] = n
        return stream(channelId, n).also { openStreams += it.liveStreamId }
    }

    override suspend fun reportPlaying(stream: OpenedStream) {
        log += "playing:${stream.liveStreamId}"
    }

    override suspend fun close(stream: StreamTicket) {
        closeGate?.await()
        if (closeFailures > 0) {
            closeFailures--
            log += "close-failed:${stream.liveStreamId}"
            throw java.io.IOException("offline")
        }
        log += "close:${stream.liveStreamId}"
        openStreams -= stream.liveStreamId
    }
}

class FakeLivePlayer(private val log: EventLog) : LivePlayer {
    var listener: LivePlayer.Listener? = null
        private set
    var playing: String? = null
    var released = false
        private set
    override val videoStarted = MutableStateFlow(false)

    override fun play(url: String) {
        log += "player.play:$url"
        playing = url
    }

    override fun stop() {
        if (playing != null) log += "player.stop"
        playing = null
    }

    override fun listenWith(listener: LivePlayer.Listener?) {
        this.listener = listener
    }

    override fun release() {
        released = true
    }
}

class FakeStreamLedger : StreamLedger {
    val tickets = linkedSetOf<StreamTicket>()
    override fun add(ticket: StreamTicket) {
        tickets += ticket
    }

    override fun remove(ticket: StreamTicket) {
        tickets -= ticket
    }

    override fun all(): List<StreamTicket> = tickets.toList()
}

/**
 * Lets everything pending finish, background work included (advanceUntilIdle skips backgroundScope,
 * where playback runs). A minute is longer than any retry or debounce.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.settle() {
    advanceTimeBy(SETTLE_MS)
    runCurrent()
}

private const val SETTLE_MS = 60_000L

class FakeLiveTvSessions : LiveTvSessions {
    var watching: List<ChannelSession> = emptyList()
    var failure: Exception? = null

    override suspend fun othersWatching(): List<ChannelSession> {
        failure?.let { throw it }
        return watching
    }
}
