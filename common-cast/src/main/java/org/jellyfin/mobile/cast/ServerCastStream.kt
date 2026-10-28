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
import kotlin.time.Duration.Companion.milliseconds

/** How long finding what a file is may take before it's cast as MP3 instead. */
private const val LOOKUP_TIMEOUT_MS = 5_000L

/**
 * [itemId] from the server as a cast device fetches it. What its file is comes from the server (as
 * it is there, whether or not it plays from a download here), once, when it's about to be cast; it
 * goes in a format a cast device plays (see [castChoiceOf]): its own where it can, kept unless
 * [keepFormat] is false, and [maxBitrate] caps it.
 */
suspend fun serverCastSource(
    apiClient: ApiClient,
    itemId: String,
    keepFormat: Boolean = true,
    maxBitrate: Int? = null,
): CastSource {
    val choice = castChoiceOf(if (keepFormat) audioFileOf(apiClient, itemId) else null, keepFormat)
    return CastSource { fromMs -> serverCastStream(apiClient, itemId, choice, fromMs, maxBitrate) }
}

/** What the server says [itemId]'s file is; null if it can't say just now. */
// Whatever the server does, the song is still cast, as MP3
@Suppress("TooGenericExceptionCaught")
private suspend fun audioFileOf(apiClient: ApiClient, itemId: String): AudioFileInfo? = try {
    withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
        withContext(Dispatchers.IO) {
            val item = apiClient.itemsApi.getItems(
                ids = listOf(itemId.toUUID()),
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
    itemId: String,
    choice: CastChoice,
    fromMs: Long,
    maxBitrate: Int? = null,
): CastStream {
    val format = choice.format
    val startMs = if (choice.asIs) 0L else fromMs.coerceAtLeast(0L)
    val url = apiClient.universalAudioApi.getUniversalAudioStreamUrl(
        itemId = itemId.toUUID(),
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
