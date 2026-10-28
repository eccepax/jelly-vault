package org.jellyfin.mobile.music.catalog

import org.jellyfin.mobile.common.server.toServerInstant
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import java.util.UUID

private const val TICKS_PER_MS = 10_000L
private const val COVER_MAX_WIDTH = 512

/** What the catalog and search share: which libraries are music, and Jellyfin item → domain mapping. */
internal class JellyfinMusicItems(private val apiClient: ApiClient) {

    /** Fields every album query needs for [toAlbum] and [isListable]. */
    val albumFields = listOf(ItemFields.DATE_CREATED, ItemFields.CHILD_COUNT)

    /**
     * Every query is scoped to these: audiobooks live on the same server, and unscoped artist
     * queries in particular mix in their authors.
     */
    suspend fun musicLibraryIds(): List<UUID> =
        apiClient.userViewsApi.getUserViews().content.items
            .filter { it.collectionType == CollectionType.MUSIC }
            .map { it.id }

    /**
     * A folder left with only .cue/.log/cover files is still an album on the server, with no tracks;
     * it isn't listed. (A null count is kept: the server didn't say.)
     */
    fun isListable(album: BaseItemDto): Boolean = (album.childCount ?: 1) > 0

    fun toAlbum(item: BaseItemDto) = Album(
        id = item.id.toString(),
        title = item.name.orEmpty(),
        artist = item.albumArtist.orEmpty(),
        year = item.productionYear,
        coverUrl = item.imageTags?.get(ImageType.PRIMARY)?.let { tag -> coverUrl(item.id, tag) },
        dateAdded = item.dateCreated?.toServerInstant(),
        isFavorite = item.userData?.isFavorite == true,
    )

    fun toTrack(item: BaseItemDto) = Track(
        id = item.id.toString(),
        title = item.name.orEmpty(),
        artist = item.artists?.joinToString().orEmpty().ifEmpty { item.albumArtist.orEmpty() },
        albumId = item.albumId?.toString().orEmpty(),
        albumTitle = item.album.orEmpty(),
        durationMs = (item.runTimeTicks ?: 0L) / TICKS_PER_MS,
        trackNumber = item.indexNumber,
        discNumber = item.parentIndexNumber,
        coverUrl = item.albumId?.let { albumId ->
            item.albumPrimaryImageTag?.let { tag -> coverUrl(albumId, tag) }
        },
        isFavorite = item.userData?.isFavorite == true,
        normalizationGainDb = item.normalizationGain,
        // The album artist's page lists this track's album; a featured artist's may not
        artistId = (item.albumArtists?.firstOrNull() ?: item.artistItems?.firstOrNull())?.id?.toString(),
    )

    fun coverUrl(itemId: UUID, tag: String) = apiClient.imageApi.getItemImageUrl(
        itemId = itemId,
        imageType = ImageType.PRIMARY,
        tag = tag,
        maxWidth = COVER_MAX_WIDTH,
    )
}
