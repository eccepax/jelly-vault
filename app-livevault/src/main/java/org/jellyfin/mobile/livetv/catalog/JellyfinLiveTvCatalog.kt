package org.jellyfin.mobile.livetv.catalog

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.common.server.toServerInstant
import org.jellyfin.mobile.livetv.domain.LiveChannel
import org.jellyfin.mobile.livetv.domain.LiveProgram
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.serializer.toUUID

/** [LiveTvCatalog] backed by the Jellyfin Live TV API. */
class JellyfinLiveTvCatalog(
    private val apiClient: ApiClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LiveTvCatalog {

    // One request for all ~400 channels: filtering and search happen client-side (see filteredBy),
    // so switching tags or typing a search never waits on the server.
    override suspend fun channels(): List<LiveChannel> = withContext(ioDispatcher) {
        apiClient.liveTvApi.getLiveTvChannels(
            addCurrentProgram = true,
            fields = listOf(ItemFields.TAGS),
            enableImageTypes = listOf(ImageType.PRIMARY),
            imageTypeLimit = 1,
            enableUserData = true,
        ).content.items
            .map(::toChannel)
            .sortedWith(compareBy(channelNumberOrder) { it.number })
    }

    override suspend fun setFavorite(channelId: String, favorite: Boolean) = withContext(ioDispatcher) {
        if (favorite) {
            apiClient.userLibraryApi.markFavoriteItem(channelId.toUUID())
        } else {
            apiClient.userLibraryApi.unmarkFavoriteItem(channelId.toUUID())
        }
        Unit
    }

    private fun toChannel(item: BaseItemDto) = LiveChannel(
        id = item.id.toString(),
        number = item.number ?: item.channelNumber.orEmpty(),
        name = item.name.orEmpty(),
        logoUrl = item.imageTags?.get(ImageType.PRIMARY)?.let { tag ->
            apiClient.imageApi.getItemImageUrl(
                itemId = item.id,
                imageType = ImageType.PRIMARY,
                tag = tag,
                maxWidth = LOGO_MAX_WIDTH,
            )
        },
        tags = item.tags.orEmpty(),
        isFavorite = item.userData?.isFavorite == true,
        nowPlaying = item.currentProgram?.let(::toProgram),
    )

    // Guide entries without both times can't be placed, so they don't count as on now.
    private fun toProgram(item: BaseItemDto): LiveProgram? {
        val start = item.startDate ?: return null
        val end = item.endDate ?: return null
        return LiveProgram(
            title = item.name.orEmpty(),
            start = start.toServerInstant(),
            end = end.toServerInstant(),
        )
    }
}

private const val LOGO_MAX_WIDTH = 256
