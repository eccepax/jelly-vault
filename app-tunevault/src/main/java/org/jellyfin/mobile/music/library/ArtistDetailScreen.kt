package org.jellyfin.mobile.music.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.domain.Album
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ArtistDetailScreen(
    state: ArtistDetailUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAlbumClick: (Album) -> Unit,
) {
    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.systemBarsPadding()) {
            TopAppBar(
                title = { if (state is ArtistDetailUiState.Loaded) Text(state.artist.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
            when (state) {
                ArtistDetailUiState.Loading -> Centered { CircularProgressIndicator() }
                ArtistDetailUiState.NotFound -> Centered { Text(stringResource(R.string.artist_not_found)) }
                ArtistDetailUiState.Error -> Centered {
                    Text(stringResource(R.string.artist_error))
                    Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(R.string.retry))
                    }
                }
                // On the artist's own page the artist is a given, so cards show the year instead.
                is ArtistDetailUiState.Loaded -> AlbumGrid(
                    albums = state.albums,
                    onAlbumClick = onAlbumClick,
                    subtitle = { it.year?.toString().orEmpty() },
                )
            }
        }
    }
}

@Composable
fun ArtistDetailRoute(artistId: String, onBack: () -> Unit, onAlbumClick: (Album) -> Unit) {
    val viewModel: ArtistDetailViewModel = koinViewModel(key = "artist:$artistId") { parametersOf(artistId) }
    val state by viewModel.state.collectAsState()
    ArtistDetailScreen(state = state, onBack = onBack, onRetry = viewModel::retry, onAlbumClick = onAlbumClick)
}
