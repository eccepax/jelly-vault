package org.jellyfin.mobile.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * An item's artwork: the [placeholder] on a surface tile until the image at [url] loads, or for
 * good when there's none. Square suits albums, audiobooks and channels; books use 2:3.
 */
@Composable
fun CoverArt(
    url: String?,
    placeholder: ImageVector,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 1f,
) {
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(aspectRatio)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colors.surface),
    ) {
        // In proportion to the tile, up to a cap: big on a grid card, still inside a list-row cover
        Icon(
            placeholder,
            contentDescription = null,
            modifier = Modifier.size(minOf(maxWidth * PLACEHOLDER_FRACTION, PLACEHOLDER_MAX_SIZE)),
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val PLACEHOLDER_FRACTION = 0.4f
private val PLACEHOLDER_MAX_SIZE = 48.dp
