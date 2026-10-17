package org.jellyfin.mobile.music.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.common.ui.components.EmptyState
import org.jellyfin.mobile.common.ui.components.ErrorState
import org.jellyfin.mobile.common.ui.components.LoadingState
import org.jellyfin.mobile.common.ui.components.VaultTopBar
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
            VaultTopBar(
                title = if (state is ArtistDetailUiState.Loaded) state.artist.name else "",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
            when (state) {
                ArtistDetailUiState.Loading -> LoadingState()
                ArtistDetailUiState.NotFound -> EmptyState(stringResource(R.string.artist_not_found))
                ArtistDetailUiState.Error -> ErrorState(stringResource(R.string.artist_error), onRetry = onRetry)
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
