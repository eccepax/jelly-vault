package org.jellyfin.mobile.music.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.ui.VaultRoot
import org.jellyfin.mobile.common.ui.startOver
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.account.SettingsRoute
import org.jellyfin.mobile.music.downloads.DownloadRepository
import org.jellyfin.mobile.music.downloads.DownloadedRoute
import org.jellyfin.mobile.music.home.HomeRoute
import org.jellyfin.mobile.music.library.AlbumDetailRoute
import org.jellyfin.mobile.music.library.AlbumsRoute
import org.jellyfin.mobile.music.library.ArtistDetailRoute
import org.jellyfin.mobile.music.library.ArtistsRoute
import org.jellyfin.mobile.music.library.LibraryScreen
import org.jellyfin.mobile.music.library.SongsRoute
import org.jellyfin.mobile.music.navigation.Browse
import org.jellyfin.mobile.music.navigation.LibraryNavigation
import org.jellyfin.mobile.music.navigation.MainBottomBar
import org.jellyfin.mobile.music.navigation.MainTab
import org.jellyfin.mobile.music.navigation.MainTabs
import org.jellyfin.mobile.music.nowplaying.PlayerShell
import org.jellyfin.mobile.music.offline.OfflineBanner
import org.jellyfin.mobile.music.offline.ServerConnectivity
import org.jellyfin.mobile.music.playback.OfflinePlayReporter
import org.jellyfin.mobile.music.playlists.AddToPlaylistHost
import org.jellyfin.mobile.music.playlists.PlaylistDetailRoute
import org.jellyfin.mobile.music.playlists.PlaylistsRoute
import org.jellyfin.mobile.music.search.SearchRoute
import org.koin.compose.koinInject

@Composable
fun TuneVaultRoot(mainViewModel: MainViewModel, appPreferences: AppPreferences) {
    VaultRoot(mainViewModel, appPreferences, logoRes = R.drawable.tune_logo) { SignedIn() }
}

/** The signed-in app: Home, Library and Search tabs over the player, each tab with its own back stack. */
@Composable
private fun SignedIn() {
    var tabIndex by rememberSaveable { mutableIntStateOf(MainTab.START.ordinal) }
    val selected = MainTab.entries[tabIndex]
    val select: (MainTab) -> Unit = { tabIndex = it.ordinal }
    OfflineUpkeep()
    // The add-to-playlist picker covers everything, so it sits beside (over) the player shell.
    Box {
        SignedInTabs(selected = selected, select = select)
        AddToPlaylistHost()
    }
}

@Composable
private fun SignedInTabs(selected: MainTab, select: (MainTab) -> Unit) {
    val connectivity: ServerConnectivity = koinInject()
    val isOffline by connectivity.isOffline.collectAsState()
    PlayerShell(
        bottomBar = {
            Column {
                if (isOffline) OfflineBanner()
                MainBottomBar(selected = selected, onSelect = select)
            }
        },
    ) {
        MainTabs(
            selected = selected,
            onSelect = select,
            home = {
                BrowseNavigation { browse ->
                    HomeRoute(
                        onAlbumClick = { album -> browse.openAlbum(album.id) },
                        onSettingsClick = browse.openSettings,
                    )
                }
            },
            library = {
                BrowseNavigation { browse ->
                    LibraryScreen(
                        albums = { AlbumsRoute(onAlbumClick = { album -> browse.openAlbum(album.id) }) },
                        artists = { ArtistsRoute(onArtistClick = { artist -> browse.openArtist(artist.id) }) },
                        songs = { SongsRoute() },
                        playlists = {
                            PlaylistsRoute(
                                onPlaylistClick = { playlist -> browse.openPlaylist(playlist.id) },
                            )
                        },
                        downloaded = {
                            DownloadedRoute(onAlbumClick = browse.openAlbum, onPlaylistClick = browse.openPlaylist)
                        },
                    )
                }
            },
            search = {
                BrowseNavigation { browse ->
                    SearchRoute(
                        onArtistClick = { artist -> browse.openArtist(artist.id) },
                        onAlbumClick = { album -> browse.openAlbum(album.id) },
                    )
                }
            },
        )
    }
}

/** A tab's back stack: [home], with album, artist and playlist pages opened on top of it. */
@Composable
private fun BrowseNavigation(home: @Composable (browse: Browse) -> Unit) {
    val activity = LocalActivity.current
    LibraryNavigation(
        home = home,
        albumDetail = { albumId, onBack -> AlbumDetailRoute(albumId = albumId, onBack = onBack) },
        artistDetail = { artistId, onBack, browse ->
            ArtistDetailRoute(artistId = artistId, onBack = onBack, onAlbumClick = { browse.openAlbum(it.id) })
        },
        playlistDetail = { playlistId, onBack -> PlaylistDetailRoute(playlistId = playlistId, onBack = onBack) },
        settings = { onBack -> SettingsRoute(onBack = onBack, onSignedOut = { activity?.startOver() }) },
    )
}

/** Keeps downloads whole at launch, and sends plays made offline whenever the Vault is reachable. */
@Composable
private fun OfflineUpkeep() {
    val downloads: DownloadRepository = koinInject()
    val connectivity: ServerConnectivity = koinInject()
    val offlinePlays: OfflinePlayReporter = koinInject()
    LaunchedEffect(Unit) { downloads.repair() }
    LaunchedEffect(Unit) {
        connectivity.isOffline.collect { offline -> if (!offline) offlinePlays.sync() }
    }
}
