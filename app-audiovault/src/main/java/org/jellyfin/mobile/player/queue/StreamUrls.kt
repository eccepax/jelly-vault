package org.jellyfin.mobile.player.queue

import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.PlayMethod

/** How the player reads a stream: as one file, or as an HLS playlist of segments. */
internal enum class StreamFormat { PROGRESSIVE, HLS }

internal data class Stream(val url: String, val format: StreamFormat)

/**
 * Where the player fetches [source] from, for its play method. The access token isn't in the
 * URL: the data source adds it as a header to requests for the server (see AppModule).
 */
internal fun ApiClient.streamOf(source: JellyfinMediaSource): Stream = when (source.playMethod) {
    PlayMethod.DIRECT_PLAY -> directPlayStream(source)
    PlayMethod.DIRECT_STREAM -> directStream(source)
    PlayMethod.TRANSCODE -> transcodeStream(source)
}

private fun ApiClient.directPlayStream(source: JellyfinMediaSource): Stream = when (source.sourceInfo.protocol) {
    MediaProtocol.FILE -> Stream(
        url = videosApi.getVideoStreamUrl(
            itemId = source.itemId,
            static = true,
            playSessionId = source.playSessionId,
            mediaSourceId = source.id,
            deviceId = deviceInfo.id,
        ),
        format = StreamFormat.PROGRESSIVE,
    )
    MediaProtocol.HTTP -> {
        val url = requireNotNull(source.sourceInfo.path)
        // Jellyfin can return either an HLS manifest URL or a progressive HTTP URL here
        Stream(url, if (url.contains(".m3u8", ignoreCase = true)) StreamFormat.HLS else StreamFormat.PROGRESSIVE)
    }
    else -> throw IllegalArgumentException("Unsupported protocol ${source.sourceInfo.protocol}")
}

private fun ApiClient.directStream(source: JellyfinMediaSource): Stream {
    val container = requireNotNull(source.sourceInfo.container) { "Missing direct stream container" }
    return Stream(
        url = videosApi.getVideoStreamByContainerUrl(
            itemId = source.itemId,
            container = container,
            playSessionId = source.playSessionId,
            mediaSourceId = source.id,
            deviceId = deviceInfo.id,
        ),
        format = StreamFormat.PROGRESSIVE,
    )
}

private fun ApiClient.transcodeStream(source: JellyfinMediaSource): Stream {
    val transcodingPath = requireNotNull(source.sourceInfo.transcodingUrl) { "Missing transcode URL" }
    // Some audio transcodes come back as progressive "http"; unknown sub-protocols are treated the same
    val format = if (source.sourceInfo.transcodingSubProtocol == MediaStreamProtocol.HLS) StreamFormat.HLS else StreamFormat.PROGRESSIVE
    return Stream(createUrl(transcodingPath), format)
}
