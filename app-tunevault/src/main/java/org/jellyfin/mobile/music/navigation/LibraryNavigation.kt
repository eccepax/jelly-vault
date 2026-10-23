package org.jellyfin.mobile.music.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import org.jellyfin.mobile.common.ui.navigation.VaultBackStack
import org.jellyfin.mobile.common.ui.navigation.rememberVaultBackStack

private const val ALBUM = "album:"
private const val ARTIST = "artist:"
private const val PLAYLIST = "playlist:"
private const val SETTINGS = "settings"

/** A page that something outside the library, such as the player, asks it to open. */
sealed interface BrowseTarget {
    data class Album(val albumId: String) : BrowseTarget
    data class Artist(val artistId: String) : BrowseTarget
}

/** What a screen in the library can open on top of itself. */
class Browse(
    val openAlbum: (albumId: String) -> Unit,
    val openArtist: (artistId: String) -> Unit,
    val openPlaylist: (playlistId: String) -> Unit,
    val openSettings: () -> Unit,
)

/**
 * The library's back stack ([VaultBackStack]): [home] (the tabs) with artists and albums opened on
 * top of it, e.g. home → artist → album. Back pops one level.
 */
@Composable
fun LibraryNavigation(
    home: @Composable (browse: Browse) -> Unit,
    albumDetail: @Composable (albumId: String, onBack: () -> Unit) -> Unit,
    artistDetail: @Composable (artistId: String, onBack: () -> Unit, browse: Browse) -> Unit,
    playlistDetail: @Composable (playlistId: String, onBack: () -> Unit) -> Unit,
    settings: @Composable (onBack: () -> Unit) -> Unit,
    /** A page to open on top of whatever's shown, once; [onOpened] says it has been. */
    open: BrowseTarget? = null,
    onOpened: () -> Unit = {},
) {
    val backStack = rememberVaultBackStack()
    val browse = remember(backStack) {
        Browse(
            openAlbum = { backStack.open(ALBUM + it) },
            openArtist = { backStack.open(ARTIST + it) },
            openPlaylist = { backStack.open(PLAYLIST + it) },
            openSettings = { backStack.open(SETTINGS) },
        )
    }

    LaunchedEffect(open) {
        when (open) {
            is BrowseTarget.Album -> browse.openAlbum(open.albumId)
            is BrowseTarget.Artist -> browse.openArtist(open.artistId)
            null -> return@LaunchedEffect
        }
        onOpened()
    }

    backStack.Show { top, close ->
        when {
            top == null -> home(browse)
            top.startsWith(ALBUM) -> albumDetail(top.removePrefix(ALBUM), close)
            top.startsWith(PLAYLIST) -> playlistDetail(top.removePrefix(PLAYLIST), close)
            top == SETTINGS -> settings(close)
            else -> artistDetail(top.removePrefix(ARTIST), close, browse)
        }
    }
}
