package org.jellyfin.mobile.music.library

import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.music.R

/** A heart; its label says what tapping does, which is also what TalkBack reads. */
@Composable
fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
            tint = if (isFavorite) MaterialTheme.colors.primary else LocalContentColor.current,
        )
    }
}

/** A heart's state and what tapping it does. */
data class FavoriteUi(val isFavorite: Boolean = false, val onToggle: () -> Unit = {})
