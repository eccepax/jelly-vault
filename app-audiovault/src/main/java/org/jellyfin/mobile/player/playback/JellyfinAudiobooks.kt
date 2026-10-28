package org.jellyfin.mobile.player.playback

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.cast.CastSource
import org.jellyfin.mobile.cast.serverCastSource
import org.jellyfin.mobile.common.server.withAccessToken
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.searchApi
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.serializer.toUUID
import java.net.URI
import java.net.URISyntaxException
import java.util.UUID

/** Audiobook formats phones play as they are; the server transcodes anything else. */
private val DIRECT_PLAY_CONTAINERS = listOf("mp3", "m4b", "m4a", "aac", "mp4", "flac", "ogg", "opus", "wav")

/** How big a cover the notification and lock screen get. */
private const val COVER_SIZE = 512

/** What playing a book needs from the server: its tracks' details, their streams and covers. */
class JellyfinAudiobooks(
    private val apiClient: ApiClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /** The items with these ids, with their chapters and where the listener left them. */
    suspend fun items(ids: List<UUID>): List<BaseItemDto> = withContext(ioDispatcher) {
        apiClient.itemsApi.getItems(
            ids = ids,
            fields = listOf(ItemFields.CHAPTERS),
            enableUserData = true,
        ).content.items.orEmpty()
    }

    /** The audiobook, or part of one, that best matches [query]; null when nothing does. */
    suspend fun topMatch(query: String): UUID? = withContext(ioDispatcher) {
        apiClient.searchApi.getSearchHints(
            searchTerm = query,
            includeItemTypes = listOf(BaseItemKind.AUDIO_BOOK, BaseItemKind.AUDIO),
            limit = 1,
        ).content.searchHints.firstOrNull()?.id
    }

    /**
     * Where the player streams [itemId] from: the file as it is when the phone plays its format,
     * otherwise an mp3 transcode, which the service's player starts again to seek in. The token
     * isn't in the URL: the player's data source sends it as a header (see AppModule).
     */
    fun streamUrl(itemId: String, directPlayContainers: List<String> = DIRECT_PLAY_CONTAINERS): String =
        apiClient.universalAudioApi.getUniversalAudioStreamUrl(
            itemId = itemId.toUUID(),
            container = directPlayContainers,
            deviceId = apiClient.deviceInfo.id,
            transcodingContainer = "mp3",
            transcodingProtocol = MediaStreamProtocol.HTTP,
            audioCodec = "mp3",
        )

    /**
     * Where a cast device gets [itemId] from (see [serverCastSource]): from the server, as the file is
     * there, even when it plays from a download here; as it is where a cast device plays that.
     */
    suspend fun castSource(itemId: String): CastSource = serverCastSource(apiClient, itemId)

    /** [uri] as a cast device fetches it alone (see [castUrl]). */
    fun castUri(uri: Uri): Uri = castUrl(uri.toString(), apiClient.baseUrl, apiClient.accessToken).toUri()

    /** The track's own cover, or its book's when it has none (one part of a book in files). */
    fun coverUrl(item: BaseItemDto): String? {
        val ownTag = item.imageTags?.get(ImageType.PRIMARY)
        val (id, tag) = when {
            ownTag != null -> item.id to ownTag
            item.albumId != null && item.albumPrimaryImageTag != null -> item.albumId to item.albumPrimaryImageTag
            else -> item.id to null
        }
        return apiClient.imageApi.getItemImageUrl(
            itemId = id ?: return null,
            imageType = ImageType.PRIMARY,
            maxWidth = COVER_SIZE,
            maxHeight = COVER_SIZE,
            tag = tag,
        )
    }
}

/**
 * [url] as a cast device fetches it, with no auth header of its own: one from the server at
 * [baseUrl] carries [token] in the url; anything else is left as it is, so the token goes nowhere else.
 * The server's is the same scheme, host and port however they're written (any case, a default port
 * said or not), under the server's own path.
 */
internal fun castUrl(url: String, baseUrl: String?, token: String?): String {
    val server = baseUrl?.let(::placeOf) ?: return url
    val asked = placeOf(url) ?: return url
    val fromServer = asked.origin == server.origin &&
        (server.path.isEmpty() || asked.path == server.path || asked.path.startsWith("${server.path}/"))
    return if (fromServer) withAccessToken(url, token) else url
}

/** Where a url points: its scheme, host and port, and its path with no slash at the end. */
private class Place(val origin: String, val path: String)

private fun placeOf(url: String): Place? {
    val uri = try {
        URI(url)
    } catch (@Suppress("SwallowedException") e: URISyntaxException) {
        return null
    }
    val scheme = uri.scheme?.lowercase() ?: return null
    val host = uri.host?.lowercase() ?: return null
    val port = when {
        uri.port != -1 -> uri.port
        scheme == "https" -> HTTPS_PORT
        scheme == "http" -> HTTP_PORT
        else -> return null
    }
    return Place("$scheme://$host:$port", uri.rawPath.orEmpty().trimEnd('/'))
}

private const val HTTP_PORT = 80
private const val HTTPS_PORT = 443
