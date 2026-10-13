package org.jellyfin.mobile.music.catalog

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

/** [HomeFeed] over Jellyfin, scoped to the music libraries like the catalog. */
class JellyfinHomeFeed(
    private val apiClient: ApiClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HomeFeed {

    private val items = JellyfinMusicItems(apiClient)

    override suspend fun recentlyPlayed(limit: Int): List<Track> = withContext(ioDispatcher) {
        items.musicLibraryIds()
            .flatMap { libraryId ->
                apiClient.itemsApi.getItems(
                    parentId = libraryId,
                    includeItemTypes = listOf(BaseItemKind.AUDIO),
                    recursive = true,
                    filters = listOf(ItemFilter.IS_PLAYED),
                    sortBy = listOf(ItemSortBy.DATE_PLAYED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    limit = limit,
                ).content.items
            }
            // Merging libraries breaks the server's order; re-sort by when each was played.
            .sortedByDescending { it.userData?.lastPlayedDate }
            .distinctBy { it.id }
            .take(limit)
            .map(items::toTrack)
    }

    override suspend fun recentlyAdded(limit: Int): List<Album> = withContext(ioDispatcher) {
        items.musicLibraryIds()
            .flatMap { libraryId ->
                apiClient.itemsApi.getItems(
                    parentId = libraryId,
                    includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                    recursive = true,
                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    fields = items.albumFields,
                    limit = limit,
                ).content.items
            }
            .filter(items::isListable)
            .distinctBy { it.id }
            .map(items::toAlbum)
            .sortedByDescending { it.dateAdded }
            .take(limit)
    }
}
