package org.jellyfin.mobile.livetv.playback

import kotlinx.coroutines.flow.StateFlow

data class StreamTicket(val channelId: String, val liveStreamId: String, val playSessionId: String?, val mediaSourceId: String)

data class OpenedStream(val channelId: String, val liveStreamId: String, val playSessionId: String?, val mediaSourceId: String, val url: String) {
    val ticket: StreamTicket get() = StreamTicket(channelId, liveStreamId, playSessionId, mediaSourceId)
}

interface LiveStreams {
    suspend fun open(channelId: String): OpenedStream
    suspend fun reportPlaying(stream: OpenedStream)
    suspend fun close(stream: StreamTicket)
}

interface LivePlayer {
    fun interface Listener { fun onError() }

    /** Whether the video is actually rendering (not just the stream opened); reset by [play] and [stop]. */
    val videoStarted: StateFlow<Boolean>

    fun play(url: String)
    fun stop()
    fun listenWith(listener: Listener?)
    fun release()
}

interface StreamLedger {
    fun add(ticket: StreamTicket)
    fun remove(ticket: StreamTicket)
    fun all(): List<StreamTicket>
}
