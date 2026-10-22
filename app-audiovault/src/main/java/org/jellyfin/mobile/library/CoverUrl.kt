package org.jellyfin.mobile.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import org.jellyfin.mobile.R
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

/** How big a list's covers are drawn, in pixels: the size to ask [CoverUrl] for. */
@Composable
fun listCoverSizePx(): Int {
    val size = dimensionResource(R.dimen.movie_thumbnail_list_size)
    return with(LocalDensity.current) { size.roundToPx() }
}
