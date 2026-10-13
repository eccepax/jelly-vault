package org.jellyfin.mobile.music.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.domain.AlbumSort

@Composable
fun SortMenu(current: AlbumSort, onSortChange: (AlbumSort) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TextButton(onClick = { open = true }) {
            Icon(Icons.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.sort_label, stringResource(current.label)))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AlbumSort.entries.forEach { sort ->
                DropdownMenuItem(
                    onClick = {
                        open = false
                        onSortChange(sort)
                    },
                    modifier = Modifier.semantics { selected = sort == current },
                ) {
                    Text(stringResource(sort.label), modifier = Modifier.width(140.dp))
                    if (sort == current) Icon(Icons.Filled.Check, contentDescription = null)
                }
            }
        }
    }
}

private val AlbumSort.label: Int
    get() = when (this) {
        AlbumSort.TITLE -> R.string.sort_title
        AlbumSort.ARTIST -> R.string.sort_artist
        AlbumSort.YEAR -> R.string.sort_year
        AlbumSort.RECENTLY_ADDED -> R.string.sort_recently_added
    }
