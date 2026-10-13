package org.jellyfin.mobile.music.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.domain.Artist
import org.jellyfin.mobile.music.domain.sortKey
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ArtistsScreen(state: ArtistsUiState, onRetry: () -> Unit, onArtistClick: (Artist) -> Unit) {
    when (state) {
        ArtistsUiState.Loading -> Centered {
            CircularProgressIndicator(modifier = Modifier.testTag(ArtistsScreenTags.LOADING))
        }
        ArtistsUiState.Error -> Centered {
            Text(stringResource(R.string.artists_error))
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.retry)) }
        }
        is ArtistsUiState.Loaded -> if (state.artists.isEmpty()) {
            Centered { Text(stringResource(R.string.artists_empty)) }
        } else {
            LazyColumn {
                items(state.artists, key = { it.id }) { artist ->
                    ArtistRow(artist = artist, onClick = { onArtistClick(artist) })
                }
            }
        }
    }
}

@Composable
internal fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colors.surface),
        ) {
            Text(text = sortKey(artist.name).take(1).uppercase(), style = MaterialTheme.typography.subtitle1)
        }
        Text(
            text = artist.name,
            style = MaterialTheme.typography.body1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
fun ArtistsRoute(onArtistClick: (Artist) -> Unit, viewModel: ArtistsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    ArtistsScreen(state = state, onRetry = viewModel::retry, onArtistClick = onArtistClick)
}
