package org.jellyfin.mobile.music.catalog

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.music.domain.Artist
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.artistsApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

/** [MusicSearch] over Jellyfin's search, scoped to the music libraries like the catalog. */
class JellyfinMusicSearch(
    private val apiClient: ApiClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MusicSearch {

    private val items = JellyfinMusicItems(apiClient)

    override suspend fun search(query: String): SearchResults = withContext(ioDispatcher) {
        val term = query.trim()
        if (term.isEmpty()) return@withContext SearchResults.EMPTY
        val libraries = items.musicLibraryIds()
        coroutineScope {
            val albums = async {
                libraries.searchItems(term, BaseItemKind.MUSIC_ALBUM).filter(items::isListable).map(items::toAlbum)
            }
            val tracks = async { libraries.searchItems(term, BaseItemKind.AUDIO).map(items::toTrack) }
            val artists = async {
                libraries
                    .flatMap {
                        apiClient.artistsApi.getAlbumArtists(
                            parentId = it,
                            searchTerm = term,
                            limit = MAX_PER_SECTION,
                        ).content.items
                    }
                    .distinctBy { it.id }
                    .take(MAX_PER_SECTION)
                    .map { Artist(id = it.id.toString(), name = it.name.orEmpty()) }
            }
            SearchResults(artists = artists.await(), albums = albums.await(), tracks = tracks.await())
        }
    }

    private suspend fun List<UUID>.searchItems(term: String, kind: BaseItemKind): List<BaseItemDto> =
        flatMap { libraryId ->
            apiClient.itemsApi.getItems(
                parentId = libraryId,
                searchTerm = term,
                includeItemTypes = listOf(kind),
                recursive = true,
                limit = MAX_PER_SECTION,
                fields = items.albumFields,
            ).content.items
        }.distinctBy { it.id }.take(MAX_PER_SECTION)

    companion object {
        const val MAX_PER_SECTION = 20
    }
}
