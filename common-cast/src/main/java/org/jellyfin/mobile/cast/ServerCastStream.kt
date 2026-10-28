package org.jellyfin.mobile.cast

import org.jellyfin.mobile.common.server.withAccessToken
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.serializer.toUUID

/** Jellyfin's ticks: 10,000 to a millisecond. */
private const val TICKS_PER_MS = 10_000L

/**
 * [itemId] from the server's universal audio stream as a cast device fetches it, in [format] (see
 * [castFormatOf]). It fetches it itself, so the token is in the url. A file that [goesAsIs] is asked
 * for as one of [CastFormat.asked], which the server sends as it is, with byte ranges: a cast device
 * seeks in it. Anything else is transcoded, which can't seek: it's asked for from [fromMs], where it
 * then starts, and asked for again for a seek (see [CastQueuePlayer]). [maxBitrate] caps either.
 */
fun serverCastStream(
    apiClient: ApiClient,
    itemId: String,
    format: CastFormat,
    goesAsIs: Boolean,
    fromMs: Long,
    maxBitrate: Int? = null,
): CastStream {
    val startMs = if (goesAsIs) 0L else fromMs.coerceAtLeast(0L)
    val url = apiClient.universalAudioApi.getUniversalAudioStreamUrl(
        itemId = itemId.toUUID(),
        // Nothing asked for as it is: the server transcodes, so the start it's asked for holds
        container = if (goesAsIs) format.asked else emptyList(),
        deviceId = apiClient.deviceInfo.id,
        maxStreamingBitrate = maxBitrate,
        transcodingContainer = format.transcodeTo,
        transcodingProtocol = MediaStreamProtocol.HTTP,
        audioCodec = format.transcodeCodec,
        maxAudioSampleRate = format.maxSampleRate,
        maxAudioBitDepth = format.maxBitDepth,
        startTimeTicks = startMs.takeIf { it > 0 }?.times(TICKS_PER_MS),
    )
    return CastStream(withAccessToken(url, apiClient.accessToken), format.mimeType, startMs, seekable = goesAsIs)
}
