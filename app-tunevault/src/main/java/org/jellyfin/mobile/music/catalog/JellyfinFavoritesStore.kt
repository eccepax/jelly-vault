package org.jellyfin.mobile.music.catalog

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.serializer.toUUID

/** [FavoritesStore] over Jellyfin's per-user favorite flag; listings are scoped to the music libraries. */
class JellyfinFavoritesStore(
    private val apiClient: ApiClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FavoritesStore {

    private val items = JellyfinMusicItems(apiClient)

    override suspend fun setFavorite(itemId: String, favorite: Boolean) = withContext(ioDispatcher) {
        if (favorite) {
            apiClient.userLibraryApi.markFavoriteItem(itemId.toUUID())
        } else {
            apiClient.userLibraryApi.unmarkFavoriteItem(itemId.toUUID())
        }
        Unit
    }

    override suspend fun favoriteTracks(): List<Track> = withContext(ioDispatcher) {
        favorites(BaseItemKind.AUDIO).map(items::toTrack)
    }

    override suspend fun favoriteAlbums(): List<Album> = withContext(ioDispatcher) {
        favorites(BaseItemKind.MUSIC_ALBUM).filter(items::isListable).map(items::toAlbum)
    }

    private suspend fun favorites(kind: BaseItemKind): List<BaseItemDto> =
        items.musicLibraryIds().flatMap { libraryId ->
            apiClient.itemsApi.getItems(
                parentId = libraryId,
                includeItemTypes = listOf(kind),
                recursive = true,
                filters = listOf(ItemFilter.IS_FAVORITE),
                sortBy = listOf(ItemSortBy.SORT_NAME),
                fields = items.albumFields,
            ).content.items
        }.distinctBy { it.id }
}
