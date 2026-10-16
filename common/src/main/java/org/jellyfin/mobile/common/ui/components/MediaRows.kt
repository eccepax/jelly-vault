package org.jellyfin.mobile.common.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.common.ui.theme.VaultSpacing

/**
 * One item in a list: a song, an audiobook, a book, a download. [highlighted] marks the one
 * playing or open. Without [onClick], a caller can put its own click handling (say, a long-press
 * menu) in [modifier]. [trailing] holds marks and actions at the end of the row.
 */
@Composable
fun MediaListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    cover: (@Composable () -> Unit)? = null,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = VaultSpacing.Large, vertical = VaultSpacing.Small),
    ) {
        if (cover != null) {
            Box(modifier = Modifier.size(LIST_COVER_SIZE)) { cover() }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (cover != null) VaultSpacing.Medium else 0.dp, end = VaultSpacing.Small),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.body1,
                color = if (highlighted) MaterialTheme.colors.primary else Color.Unspecified,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) SecondaryText(subtitle, maxLines = 1)
        }
        trailing()
    }
}

/**
 * One item in a grid or a shelf: its cover, with the title and a subtitle under it. [onLongClick]
 * opens whatever the item offers besides opening it, such as downloading.
 */
@Composable
fun MediaGridCard(
    title: String,
    subtitle: String?,
    cover: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Column(modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        cover()
        Text(
            text = title,
            style = MaterialTheme.typography.subtitle2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = VaultSpacing.Small),
        )
        if (subtitle != null) SecondaryText(subtitle, maxLines = 1)
    }
}

private val LIST_COVER_SIZE = 48.dp
