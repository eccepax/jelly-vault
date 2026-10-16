package org.jellyfin.mobile.music.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

private const val ALBUM = "album:"
private const val ARTIST = "artist:"
private const val PLAYLIST = "playlist:"
private const val SETTINGS = "settings"
private const val HOME = "home"

private val backStackSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

/**
 * The library's back stack: [home] (the tabs) with artists and albums opened on top of it, e.g.
 * home → artist → album. Back pops one level. Only ids are saved, so the stack survives rotation and
 * process death, and each level keeps its own saved state (scroll position) while covered.
 */
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
    val stack = rememberSaveable(saver = backStackSaver) { mutableStateListOf() }
    val savedStates = rememberSaveableStateHolder()

    // Keyed by depth too, so the same album opened at two depths keeps separate state.
    fun keyAt(depth: Int) = "$depth:${stack.getOrNull(depth - 1) ?: HOME}"
    val pop: () -> Unit = {
        savedStates.removeState(keyAt(stack.size))
        stack.removeAt(stack.lastIndex)
    }
    val browse = Browse(
        openAlbum = { stack += ALBUM + it },
        openArtist = { stack += ARTIST + it },
        openPlaylist = { stack += PLAYLIST + it },
        openSettings = { stack += SETTINGS },
    )

    LaunchedEffect(open) {
        when (open) {
            is BrowseTarget.Album -> browse.openAlbum(open.albumId)
            is BrowseTarget.Artist -> browse.openArtist(open.artistId)
            null -> return@LaunchedEffect
        }
        onOpened()
    }

    BackHandler(enabled = stack.isNotEmpty(), onBack = pop)

    val top = stack.lastOrNull()
    savedStates.SaveableStateProvider(keyAt(stack.size)) {
        when {
            top == null -> home(browse)
            top.startsWith(ALBUM) -> albumDetail(top.removePrefix(ALBUM), pop)
            top.startsWith(PLAYLIST) -> playlistDetail(top.removePrefix(PLAYLIST), pop)
            top == SETTINGS -> settings(pop)
            else -> artistDetail(top.removePrefix(ARTIST), pop, browse)
        }
    }
}
