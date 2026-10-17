package org.jellyfin.mobile.music.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import org.jellyfin.mobile.common.ui.components.EmptyState
import org.jellyfin.mobile.common.ui.components.ErrorState
import org.jellyfin.mobile.common.ui.components.LoadingState
import org.jellyfin.mobile.common.ui.components.MediaListRow
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.domain.Artist
import org.jellyfin.mobile.music.domain.sortKey
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ArtistsScreen(state: ArtistsUiState, onRetry: () -> Unit, onArtistClick: (Artist) -> Unit) {
    when (state) {
        ArtistsUiState.Loading -> LoadingState(Modifier.testTag(ArtistsScreenTags.LOADING))
        ArtistsUiState.Error -> ErrorState(stringResource(R.string.artists_error), onRetry = onRetry)
        is ArtistsUiState.Loaded -> if (state.artists.isEmpty()) {
            EmptyState(stringResource(R.string.artists_empty))
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
    MediaListRow(
        title = artist.name,
        onClick = onClick,
        cover = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colors.surface),
            ) {
                Text(text = sortKey(artist.name).take(1).uppercase(), style = MaterialTheme.typography.subtitle1)
            }
        },
    )
}

@Composable
fun ArtistsRoute(onArtistClick: (Artist) -> Unit, viewModel: ArtistsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    ArtistsScreen(state = state, onRetry = viewModel::retry, onArtistClick = onArtistClick)
}
