package org.jellyfin.mobile.music.catalog

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.music.domain.Playlist
import org.jellyfin.mobile.music.domain.PlaylistEntry
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CreatePlaylistDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.serializer.toUUID

private val PLAYLIST_FILE_EXTENSIONS = listOf(".m3u", ".m3u8", ".pls", ".wpl", ".xspf")

/**
 * Album downloads often ship a playlist file of their own tracks, which Jellyfin imports as a playlist
 * that only duplicates the album. Playlists made in Jellyfin live in its data folder instead. (Media
 * type doesn't tell them apart: imported ones are mostly Unknown, but some come back as Audio.)
 */
private fun BaseItemDto.isImportedPlaylistFile(): Boolean =
    PLAYLIST_FILE_EXTENSIONS.any { path.orEmpty().endsWith(it, ignoreCase = true) }

/** [PlaylistStore] over the user's Jellyfin playlists (not video ones, nor ones imported from album folders). */
class JellyfinPlaylistStore(
    private val apiClient: ApiClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PlaylistStore {

    private val items = JellyfinMusicItems(apiClient)

    override suspend fun playlists(): List<Playlist> = withContext(ioDispatcher) {
        apiClient.itemsApi.getItems(
            includeItemTypes = listOf(BaseItemKind.PLAYLIST),
            recursive = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            fields = listOf(ItemFields.CHILD_COUNT, ItemFields.PATH),
        ).content.items
            .filter { it.mediaType != MediaType.VIDEO && !it.isImportedPlaylistFile() }
            .map { item ->
                Playlist(
                    id = item.id.toString(),
                    name = item.name.orEmpty(),
                    trackCount = item.childCount ?: 0,
                    coverUrl = item.imageTags?.get(ImageType.PRIMARY)?.let { tag -> items.coverUrl(item.id, tag) },
                )
            }
    }

    override suspend fun playlistEntries(playlistId: String): List<PlaylistEntry> = withContext(ioDispatcher) {
        apiClient.playlistsApi.getPlaylistItems(playlistId = playlistId.toUUID()).content.items
            .mapNotNull { item ->
                item.playlistItemId?.let {
                    PlaylistEntry(
                        entryId = it,
                        track = items.toTrack(item),
                    )
                }
            }
    }

    override suspend fun createPlaylist(name: String, trackIds: List<String>): String = withContext(ioDispatcher) {
        apiClient.playlistsApi.createPlaylist(
            CreatePlaylistDto(
                name = name,
                ids = trackIds.map { it.toUUID() },
                mediaType = MediaType.AUDIO,
                users = emptyList(),
                isPublic = false,
            ),
        ).content.id
            // Creation answers with a dashless id; listings use the dashed form. Normalise so they match.
            .toUUID().toString()
    }

    override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>) = withContext(ioDispatcher) {
        apiClient.playlistsApi.addItemToPlaylist(playlistId = playlistId.toUUID(), ids = trackIds.map { it.toUUID() })
        Unit
    }

    override suspend fun removeFromPlaylist(playlistId: String, entryId: String) = withContext(ioDispatcher) {
        apiClient.playlistsApi.removeItemFromPlaylist(playlistId = playlistId, entryIds = listOf(entryId))
        Unit
    }

    override suspend fun deletePlaylist(playlistId: String) = withContext(ioDispatcher) {
        apiClient.libraryApi.deleteItem(playlistId.toUUID())
        Unit
    }
}
