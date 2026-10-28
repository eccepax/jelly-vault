package org.jellyfin.mobile.livetv.playback

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class StreamTicket(val channelId: String, val liveStreamId: String, val playSessionId: String?, val mediaSourceId: String)

data class OpenedStream(val channelId: String, val liveStreamId: String, val playSessionId: String?, val mediaSourceId: String, val url: String) {
    val ticket: StreamTicket get() = StreamTicket(channelId, liveStreamId, playSessionId, mediaSourceId)
}

interface LiveStreams {
    /** Opens [channelId]: [forCast], as a stream a cast device plays (HLS, with the token in its url). */
    suspend fun open(channelId: String, forCast: Boolean = false): OpenedStream
    suspend fun reportPlaying(stream: OpenedStream)
    suspend fun close(stream: StreamTicket)
}

interface LivePlayer {
    fun interface Listener {
        fun onError()

        /** The cast device stopped playing the channel by itself (its remote, another app) while still connected. */
        fun onStoppedElsewhere() = Unit
    }

    /** Whether the video is actually rendering (not just the stream opened); reset by [play] and [stop]. */
    val videoStarted: Flow<Boolean>

    /** Whether [play] goes to a cast device, which needs a stream opened for it (see [LiveStreams.open]). */
    val onCastDevice: StateFlow<Boolean>

    /** Plays [url], the stream of [channelId]. */
    fun play(url: String, channelId: String)
    fun stop()
    fun listenWith(listener: Listener?)
    fun release()
}

interface StreamLedger {
    fun add(ticket: StreamTicket)
    fun remove(ticket: StreamTicket)
    fun all(): List<StreamTicket>
}
