package org.jellyfin.mobile.library

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

/** An item's cover on the server, [sizePx] square at most; screens take it so tests needn't reach a server. */
fun interface CoverUrl {
    operator fun invoke(itemId: UUID, sizePx: Int): String?
}

fun jellyfinCoverUrl(apiClient: ApiClient) = CoverUrl { itemId, sizePx ->
    apiClient.imageApi.getItemImageUrl(
        itemId = itemId,
        imageType = ImageType.PRIMARY,
        maxWidth = sizePx,
        maxHeight = sizePx,
    )
}
