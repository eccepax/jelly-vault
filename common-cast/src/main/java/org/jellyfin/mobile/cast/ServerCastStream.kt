package org.jellyfin.mobile.cast

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.mobile.common.server.withAccessToken
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.extensions.inWholeTicks
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

/** How long finding what a file is may take before it's cast as MP3 instead. */
private const val LOOKUP_TIMEOUT_MS = 5_000L

/**
 * [itemId] from the server as a cast device fetches it. What its file is comes from the server (as
 * it is there, whether or not it plays from a download here), once, when it's about to be cast, and
 * only if [keepFormat] says to keep it then; it goes in a format a cast device plays (see
 * [castChoiceOf]): its own where it can, kept unless [keepFormat] says not, and [maxBitrate] caps
 * it. Both are asked each time a stream is, so a change of quality while casting reaches the songs
 * already found (one found while not keeping formats goes as MP3). An id that isn't one throws.
 */
suspend fun serverCastSource(
    apiClient: ApiClient,
    itemId: String,
    keepFormat: () -> Boolean = { true },
    maxBitrate: () -> Int? = { null },
): CastSource {
    val id = itemId.toUUID()
    val file = if (keepFormat()) audioFileOf(apiClient, id) else null
    return CastSource { fromMs ->
        val keep = keepFormat()
        serverCastStream(apiClient, id, castChoiceOf(if (keep) file else null, keep), fromMs, maxBitrate())
    }
}

/** What the server says [itemId]'s file is; null if it can't say just now. */
// Whatever the server does, the song is still cast, as MP3
@Suppress("TooGenericExceptionCaught")
private suspend fun audioFileOf(apiClient: ApiClient, itemId: UUID): AudioFileInfo? = try {
    withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            val item = apiClient.itemsApi.getItems(
                ids = listOf(itemId),
                fields = listOf(ItemFields.MEDIA_SOURCES),
            ).content.items.firstOrNull()
            val source = item?.mediaSources?.firstOrNull() ?: return@withContext null
            val audio = source.mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }
            AudioFileInfo(source.container, audio?.codec, audio?.sampleRate, audio?.bitDepth)
        }
    }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Timber.w(e, "Couldn't find what %s is, casting it as MP3", itemId)
    null
}

/**
 * [itemId] from the server's universal audio stream as a cast device fetches it, as [choice] says.
 * It fetches it itself, so the token is in the url. A file that goes as it is is asked for as one of
 * [CastFormat.asked], which the server sends as it is, with byte ranges: a cast device seeks in it.
 * Anything else is transcoded, which can't seek: it's asked for from [fromMs], where it then starts,
 * and asked for again for a seek (see [CastQueuePlayer]). [maxBitrate] caps either.
 */
internal fun serverCastStream(
    apiClient: ApiClient,
    itemId: UUID,
    choice: CastChoice,
    fromMs: Long,
    maxBitrate: Int? = null,
): CastStream {
    val format = choice.format
    val startMs = if (choice.asIs) 0L else fromMs.coerceAtLeast(0L)
    val url = apiClient.universalAudioApi.getUniversalAudioStreamUrl(
        itemId = itemId,
        // Nothing asked for as it is: the server transcodes, so the start it's asked for holds
        container = if (choice.asIs) format.asked else emptyList(),
        deviceId = apiClient.deviceInfo.id,
        maxStreamingBitrate = maxBitrate,
        transcodingContainer = format.transcodeTo,
        transcodingProtocol = MediaStreamProtocol.HTTP,
        audioCodec = format.transcodeCodec,
        maxAudioSampleRate = format.maxSampleRate,
        maxAudioBitDepth = format.maxBitDepth,
        startTimeTicks = startMs.takeIf { it > 0 }?.milliseconds?.inWholeTicks,
    )
    return CastStream(withAccessToken(url, apiClient.accessToken), format.mimeType, startMs, seekable = choice.asIs)
}

/** Every format a cast device plays as it is (see [castFormatOf]): the server picks, file by file. */
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
        transcodingContainer = CAST_MP3.transcodeTo,
        transcodingProtocol = MediaStreamProtocol.HTTP,
        audioCodec = CAST_MP3.transcodeCodec,
        maxAudioSampleRate = CAST_MAX_SAMPLE_RATE,
        maxAudioBitDepth = CAST_MAX_BIT_DEPTH,
    )
    return CastStream(withAccessToken(url, apiClient.accessToken), CAST_MP3.mimeType)
}
