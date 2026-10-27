package org.jellyfin.mobile.music.catalog

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.cast.CastStream
import org.jellyfin.mobile.common.server.withAccessToken
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Artist
import org.jellyfin.mobile.music.domain.StreamingQuality
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.serializer.toUUID
import java.util.UUID

/** Containers ExoPlayer plays natively, so the server can direct-stream them untouched. */
private val DIRECT_PLAY_CONTAINERS = listOf("flac", "mp3", "aac", "m4a", "mp4", "ogg", "opus", "wav")

/**
 * How a cast device gets a song in a format it plays (Google's supported media list): the [asked]
 * containers come as they are, each only with the codecs named (`container|codec`) where it holds
 * others too; anything else, or more than a cast device decodes (over 96 kHz or 24 bits), is
 * transcoded to [transcodeTo] in [transcodeCodec]. Either way it's served as [mimeType], so the type
 * the cast device is told is the one it gets. Not m4a or mp4: they hold ALAC too, which cast devices
 * can't play, and the server can't be asked for them by codec alone.
 */
private class CastFormat(
    val asked: List<String>,
    val transcodeTo: String,
    val transcodeCodec: String,
    val mimeType: String,
)

private val CAST_FLAC = CastFormat(listOf("flac"), "flac", "flac", "audio/flac")
private val CAST_MP3 = CastFormat(listOf("mp3"), "mp3", "mp3", "audio/mpeg")

/** By the song's container; lossless ones (wav too) as FLAC, which is smaller and plays alike. */
private val CAST_FORMATS = mapOf(
    "flac" to CAST_FLAC,
    "wav" to CAST_FLAC,
    "mp3" to CAST_MP3,
    "aac" to CastFormat(listOf("aac"), "aac", "aac", "audio/aac"),
    "ogg" to CastFormat(listOf("ogg|vorbis", "ogg|opus"), "ogg", "opus", "audio/ogg"),
    "opus" to CastFormat(listOf("opus|opus"), "ogg", "opus", "audio/ogg"),
    "webm" to CastFormat(listOf("webm|opus", "webm|vorbis"), "webm", "opus", "audio/webm"),
)

/** The most a cast device decodes (Google's supported media list: FLAC up to 96 kHz, 24 bits). */
private const val CAST_MAX_SAMPLE_RATE = 96_000
private const val CAST_MAX_BIT_DEPTH = 24

/**
 * [MusicCatalog] backed by the Jellyfin API. Every query is scoped to the user's music libraries
 * (see [JellyfinMusicItems.musicLibraryIds]).
 * Results merged across libraries are de-duplicated by id, since overlapping libraries (two over the
 * same folder, or nested folders) return the same items from each.
 */
class JellyfinMusicCatalog(
    private val apiClient: ApiClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /** Read for every stream, so a change applies from the next song. */
    private val streamingQuality: () -> StreamingQuality = { StreamingQuality.ORIGINAL },
) : MusicCatalog {

    private val items = JellyfinMusicItems(apiClient)

    override suspend fun albums(): List<Album> = withContext(ioDispatcher) {
        items.musicLibraryIds()
            .flatMap { libraryId -> albumItems(parentId = libraryId) }
            .distinctBy { it.id }
            .map(items::toAlbum)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    // An id filter rather than GET /Items/{id}: the server answers an unknown id there with a 500,
    // not a 404, which is indistinguishable from a real server error.
    override suspend fun album(albumId: String): Album? = withContext(ioDispatcher) {
        apiClient.itemsApi.getItems(
            ids = listOf(albumId.toUUID()),
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            recursive = true,
            fields = items.albumFields,
        ).content.items.firstOrNull()?.let(items::toAlbum)
    }

    override suspend fun tracks(): List<Track> = withContext(ioDispatcher) {
        items.musicLibraryIds()
            .flatMap { libraryId ->
                apiClient.itemsApi.getItems(
                    parentId = libraryId,
                    includeItemTypes = listOf(BaseItemKind.AUDIO),
                    recursive = true,
                    sortBy = listOf(ItemSortBy.SORT_NAME),
                ).content.items
            }
            .distinctBy { it.id }
            .map(items::toTrack)
    }

    override suspend fun albumTracks(albumId: String): List<Track> = withContext(ioDispatcher) {
        apiClient.itemsApi.getItems(
            parentId = albumId.toUUID(),
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            // Recursive: the plain children listing returns stale per-user data (a just-set favorite
            // reads false there, found by the favorites contract test), and it also reaches tracks
            // kept in per-disc subfolders.
            recursive = true,
            sortBy = listOf(ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER, ItemSortBy.SORT_NAME),
        ).content.items.map(items::toTrack)
    }

    // Derived from the listed albums (rather than the server's album-artist list) so every artist
    // shown has at least one non-empty album to open.
    override suspend fun albumArtists(): List<Artist> = withContext(ioDispatcher) {
        items.musicLibraryIds()
            .flatMap { libraryId -> albumItems(parentId = libraryId) }
            .flatMap { it.albumArtists.orEmpty() }
            .distinctBy { it.id }
            .map { Artist(id = it.id.toString(), name = it.name.orEmpty()) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    // An id filter, like album(): GET /Items/{unknownId} answers 500 rather than 404.
    override suspend fun artist(artistId: String): Artist? = withContext(ioDispatcher) {
        val id = artistId.toUUID()
        val item = apiClient.itemsApi.getItems(
            ids = listOf(id),
            includeItemTypes = listOf(BaseItemKind.MUSIC_ARTIST),
            recursive = true,
        ).content.items.firstOrNull() ?: return@withContext null
        // Named as its albums credit it, matching albumArtists(): Jellyfin 12 strips characters such
        // as quotes from the artist item's own name (Avery "Tone" Adams becomes Avery Tone Adams).
        val credited = items.musicLibraryIds().firstNotNullOfOrNull { libraryId ->
            albumItems(parentId = libraryId, albumArtistId = id).firstNotNullOfOrNull { album ->
                album.albumArtists?.firstOrNull { it.id == id }?.name
            }
        }
        Artist(id = item.id.toString(), name = credited ?: item.name.orEmpty())
    }

    override suspend fun artistAlbums(artistId: String): List<Album> = withContext(ioDispatcher) {
        items.musicLibraryIds()
            .flatMap { libraryId -> albumItems(parentId = libraryId, albumArtistId = artistId.toUUID()) }
            .distinctBy { it.id }
            .map(items::toAlbum)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    override fun streamUrl(trackId: String): String =
        // The SDK's URL builder can't include credentials, and ExoPlayer requests don't carry the
        // SDK's auth header, so authenticate via the query string.
        withAccessToken(downloadUri(trackId, streamingQuality()), apiClient.accessToken)

    /**
     * The song as a cast device gets it, and the type it comes as (the receiver needs one): it
     * fetches the song itself, so the token is in the url. At the original quality, in its own
     * format where a cast device plays that (see [CastFormat]); anything else, or at a lower
     * quality, as MP3. Either way the type is known before the server answers.
     */
    fun castStream(trackId: String, container: String?): CastStream {
        val quality = streamingQuality()
        val format = container?.lowercase()?.takeIf { quality == StreamingQuality.ORIGINAL }
            ?.let(CAST_FORMATS::get)
            ?: CAST_MP3
        val url = apiClient.universalAudioApi.getUniversalAudioStreamUrl(
            itemId = trackId.toUUID(),
            container = format.asked,
            maxStreamingBitrate = quality.maxBitrate,
            transcodingContainer = format.transcodeTo,
            transcodingProtocol = MediaStreamProtocol.HTTP,
            audioCodec = format.transcodeCodec,
            maxAudioSampleRate = CAST_MAX_SAMPLE_RATE,
            maxAudioBitDepth = CAST_MAX_BIT_DEPTH,
        )
        return CastStream(withAccessToken(url, apiClient.accessToken), format.mimeType)
    }

    /**
     * Where to fetch [trackId] from at [quality], without a token: downloads store this URI for as
     * long as they last, and get the current token added to each request (see [withAccessToken]).
     */
    fun downloadUri(trackId: String, quality: StreamingQuality): String =
        universalAudioUri(trackId, DIRECT_PLAY_CONTAINERS, quality, transcodeTo = "m4a", transcodeCodec = "aac")

    /** [trackId] as it is if it's in one of [containers] (and within [quality]), else as [transcodeTo]. */
    private fun universalAudioUri(
        trackId: String,
        containers: List<String>,
        quality: StreamingQuality,
        transcodeTo: String,
        transcodeCodec: String,
    ): String = apiClient.universalAudioApi.getUniversalAudioStreamUrl(
        itemId = trackId.toUUID(),
        container = containers,
        maxStreamingBitrate = quality.maxBitrate,
        transcodingContainer = transcodeTo,
        transcodingProtocol = MediaStreamProtocol.HTTP,
        audioCodec = transcodeCodec,
    )

    private suspend fun albumItems(parentId: UUID, albumArtistId: UUID? = null): List<BaseItemDto> =
        apiClient.itemsApi.getItems(
            parentId = parentId,
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            recursive = true,
            albumArtistIds = albumArtistId?.let(::listOf),
            sortBy = listOf(ItemSortBy.SORT_NAME),
            fields = items.albumFields,
        ).content.items
            .filter(items::isListable)
}
