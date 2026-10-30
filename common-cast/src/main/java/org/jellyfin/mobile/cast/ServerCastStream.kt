package org.jellyfin.mobile.cast

import org.jellyfin.mobile.common.server.withAccessToken
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.serializer.toUUID

/** Every format a cast device plays as it is (Google's supported media list): the server picks, file by file. */
private val CAST_AS_IS =
    listOf("mp3", "flac", "aac", "m4a|aac", "m4b|aac", "mp4|aac", "ogg|vorbis", "ogg|opus", "opus|opus", "webm|opus")

/**
 * The most a file sent as it is may carry: more than cast devices decode (FLAC up to 96 kHz, 24 bits)
 * goes transcoded. 48 kHz, not 96: Jellyfin passes the cap on to the MP3 it transcodes to, and its
 * MP3 encoder fails over 48 kHz (a 192 kHz FLAC asked for with a 96 kHz cap gets an error, with 48
 * kHz an MP3). An 88.2 or 96 kHz file goes as MP3 then, which every cast device plays.
 */
internal const val CAST_MAX_SAMPLE_RATE = 48_000
internal const val CAST_MAX_BIT_DEPTH = 24

/**
 * [itemId] from the server as a cast device fetches it, known at once, with nothing asked of the
 * server first: as it is where it's in a format a cast device plays (and [keepFormat] says to keep
 * it) and within [CAST_MAX_SAMPLE_RATE] and [CAST_MAX_BIT_DEPTH], else transcoded to MP3;
 * [maxBitrate] caps either. The type sent is MP3's either way, since which it'll be isn't known
 * here: the Default Media Receiver plays what the server sends whatever it's told (the server's own
 * Content-Type is right).
 */
fun serverCastStreamNow(
    apiClient: ApiClient,
    itemId: String,
    keepFormat: Boolean = true,
    maxBitrate: Int? = null,
): CastStream {
    val url = apiClient.universalAudioApi.getUniversalAudioStreamUrl(
        itemId = itemId.toUUID(),
        container = if (keepFormat) CAST_AS_IS else emptyList(),
        deviceId = apiClient.deviceInfo.id,
        maxStreamingBitrate = maxBitrate,
        transcodingContainer = "mp3",
        transcodingProtocol = MediaStreamProtocol.HTTP,
        audioCodec = "mp3",
        maxAudioSampleRate = CAST_MAX_SAMPLE_RATE,
        maxAudioBitDepth = CAST_MAX_BIT_DEPTH,
    )
    return CastStream(withAccessToken(url, apiClient.accessToken), "audio/mpeg")
}
