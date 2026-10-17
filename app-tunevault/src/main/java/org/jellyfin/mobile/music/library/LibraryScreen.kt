package org.jellyfin.mobile.music.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ScrollableTabRow
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.common.ui.components.VaultTopBar
import org.jellyfin.mobile.music.R

private val TABS = listOf(
    R.string.albums_title,
    R.string.artists_title,
    R.string.songs_title,
    R.string.playlists_title,
    R.string.downloaded,
)

/** The Library header with Albums / Artists / Songs / Playlists / Downloaded tabs; each keeps its scroll position. */
@Composable
fun LibraryScreen(
    albums: @Composable () -> Unit,
    artists: @Composable () -> Unit,
    songs: @Composable () -> Unit,
    playlists: @Composable () -> Unit,
    downloaded: @Composable () -> Unit,
) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tabStates = rememberSaveableStateHolder()
    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.systemBarsPadding()) {
            VaultTopBar(title = stringResource(R.string.library_title))
            ScrollableTabRow(selectedTabIndex = selected, edgePadding = 0.dp) {
                TABS.forEachIndexed { index, label ->
                    Tab(
                        selected = index == selected,
                        onClick = { selected = index },
                        text = { Text(stringResource(label)) },
                    )
                }
            }
            tabStates.SaveableStateProvider(selected) {
                when (TABS[selected]) {
                    R.string.albums_title -> albums()
                    R.string.artists_title -> artists()
                    R.string.songs_title -> songs()
                    R.string.playlists_title -> playlists()
                    else -> downloaded()
                }
            }
        }
    }
}
