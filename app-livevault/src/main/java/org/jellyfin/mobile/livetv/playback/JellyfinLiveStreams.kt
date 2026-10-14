package org.jellyfin.mobile.livetv.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.common.server.withAccessToken
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.hlsSegmentApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber
import java.util.Collections

/**
 * [LiveStreams] over Jellyfin: the server opens the tuner stream (AutoOpenLiveStream) when asked
 * for playback info, and serves it direct or as HLS depending on [deviceProfile].
 */
class JellyfinLiveStreams(
    private val apiClient: ApiClient,
    private val deviceProfile: () -> DeviceProfile,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LiveStreams {

    /** Opens whose start has been reported (by [key]); later reports are progress. */
    private val started: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    override suspend fun open(channelId: String): OpenedStream = withContext(ioDispatcher) {
        val info = apiClient.mediaInfoApi.getPostedPlaybackInfo(
            itemId = channelId.toUUID(),
            data = PlaybackInfoDto(
                deviceProfile = deviceProfile(),
                autoOpenLiveStream = true,
                enableDirectPlay = true,
                enableDirectStream = true,
                enableTranscoding = true,
            ),
        ).content
        val source = info.mediaSources.firstOrNull() ?: error("No stream for channel $channelId")
        val liveStreamId = source.liveStreamId ?: error("Channel $channelId didn't open a live stream")
        val url = playableUrl(channelId, source, liveStreamId)
        if (url == null) {
            // Opened but unplayable: don't leave it holding the tuner.
            apiClient.mediaInfoApi.closeLiveStream(liveStreamId)
            error("No playable stream for channel $channelId")
        }
        OpenedStream(
            channelId = channelId,
            liveStreamId = liveStreamId,
            playSessionId = info.playSessionId,
            mediaSourceId = source.id.orEmpty(),
            url = url,
        )
    }

    private fun playableUrl(channelId: String, source: MediaSourceInfo, liveStreamId: String): String? = when {
        source.supportsDirectStream -> withAccessToken(
            apiClient.videosApi.getVideoStreamUrl(
                itemId = channelId.toUUID(),
                static = true,
                mediaSourceId = source.id,
                liveStreamId = liveStreamId,
            ),
            apiClient.accessToken,
        )
        // Relative, and already carries the token (ApiKey).
        source.transcodingUrl != null -> apiClient.baseUrl.orEmpty().trimEnd('/') + source.transcodingUrl
        else -> null
    }

    override suspend fun reportPlaying(stream: OpenedStream) = withContext(ioDispatcher) {
        if (started.add(stream.ticket.key)) {
            apiClient.playStateApi.reportPlaybackStart(
                PlaybackStartInfo(
                    itemId = stream.channelId.toUUID(),
                    mediaSourceId = stream.mediaSourceId,
                    liveStreamId = stream.liveStreamId,
                    playSessionId = stream.playSessionId,
                    playMethod = stream.playMethod,
                    canSeek = false,
                    isPaused = false,
                    isMuted = false,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                ),
            )
        } else {
            apiClient.playStateApi.reportPlaybackProgress(
                PlaybackProgressInfo(
                    itemId = stream.channelId.toUUID(),
                    mediaSourceId = stream.mediaSourceId,
                    liveStreamId = stream.liveStreamId,
                    playSessionId = stream.playSessionId,
                    playMethod = stream.playMethod,
                    canSeek = false,
                    isPaused = false,
                    isMuted = false,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                ),
            )
        }
        Unit
    }

    /**
     * Reports the stop and ends any transcode (best effort: the server 500s a stop report for a
     * stream it no longer knows), then closes the live stream, which is what frees the tuner.
     * Closing a stream that's already gone succeeds.
     *
     * The server shares one tuner stream between everyone watching a channel and counts them.
     * A stop report that names the live stream ALSO takes one off that count, so it doesn't name
     * it here: closing must take off exactly one, or it cuts off another viewer of the channel.
     */
    override suspend fun close(stream: StreamTicket) = withContext(ioDispatcher) {
        started -= stream.key
        bestEffort("report stop") {
            apiClient.playStateApi.reportPlaybackStopped(
                PlaybackStopInfo(
                    itemId = stream.channelId.toUUID(),
                    mediaSourceId = stream.mediaSourceId,
                    playSessionId = stream.playSessionId,
                    failed = false,
                ),
            )
        }
        stream.playSessionId?.let { playSessionId ->
            bestEffort("stop transcode") {
                apiClient.hlsSegmentApi.stopEncodingProcess(
                    deviceId = apiClient.deviceInfo.id,
                    playSessionId = playSessionId,
                )
            }
        }
        apiClient.mediaInfoApi.closeLiveStream(stream.liveStreamId)
        Unit
    }

    private suspend fun bestEffort(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Timber.w(e, "Couldn't %s", what)
        }
    }

    /**
     * One open of a stream. Not the LiveStreamId: the server gives every open of a channel the same
     * one while the tuner stream is shared.
     */
    private val StreamTicket.key get() = playSessionId ?: liveStreamId

    private val OpenedStream.playMethod
        get() = if (".m3u8" in url) PlayMethod.TRANSCODE else PlayMethod.DIRECT_STREAM
}
