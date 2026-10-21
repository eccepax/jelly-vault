package org.jellyfin.mobile.library

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.downloads.DownloadRequests
import org.jellyfin.mobile.downloads.rememberDownloadRequests
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.util.UUID

/**
 * Browsing and playing the server's audiobooks. Tapping a book plays it from where it was left
 * ([onPlay]); holding one offers to download, remove or hide it. Back goes up a folder, while
 * there's one to go up to.
 */
// Each parameter is one way out of the library, or a stand-in for tests.
@Suppress("LongParameterList")
@Composable
fun LibraryRoute(
    onPlay: (PlayOptions) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
    playerViewModel: AudiobookPlayerViewModel = koinViewModel(),
    downloads: DownloadRequests = rememberDownloadRequests(),
    coverUrl: CoverUrl = koinInject(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val coverSize = with(LocalDensity.current) { dimensionResource(R.dimen.movie_thumbnail_list_size).roundToPx() }
    var itemMenu by remember { mutableStateOf<ItemMenu?>(null) }
    val canGoUp by viewModel.canGoUp.collectAsState()

    BackHandler(enabled = canGoUp) { viewModel.goUp() }
    // Settings change on their own screen; the list reloads as it comes back if one it shows did
    LifecycleResumeEffect(viewModel) {
        viewModel.checkAndReloadIfSettingChanged()
        onPauseOrDispose {}
    }
    ReloadWhenPlaybackStops(viewModel, playerViewModel)

    val opener = ItemOpener(viewModel, scope) { queue -> onPlay(queue.toPlayOptions()) }
    val menus = ItemMenus(context, viewModel, downloads, scope) { itemMenu = it }
    LibraryScreen(
        state = libraryState(viewModel, playerViewModel, itemMenu) { coverUrl(it, coverSize) },
        actions = LibraryActions(
            onUp = { viewModel.goUp() },
            onQueryChange = viewModel::setFilterQuery,
            onSort = viewModel::setSortOption,
            onDownloadedOnly = { viewModel.showDownloadedOnly.value = it },
            onShowHidden = { viewModel.showHiddenItems.value = it },
            onSettings = onOpenSettings,
            onRefresh = { viewModel.refresh(force = true) },
            onOpen = opener::open,
            onLongPress = menus::open,
            onDismissMenu = { itemMenu = null },
            onOpenPlayer = onOpenPlayer,
            onPlayPause = playerViewModel::togglePlayPause,
            onStop = playerViewModel::stop,
            coverUrl = { coverUrl(it, coverSize) },
        ),
    )
}

@Composable
private fun libraryState(
    viewModel: LibraryViewModel,
    playerViewModel: AudiobookPlayerViewModel,
    itemMenu: ItemMenu?,
    coverUrl: (UUID) -> String?,
): LibraryUiState {
    val title by viewModel.title.collectAsState()
    val canGoUp by viewModel.canGoUp.collectAsState()
    val items by viewModel.items.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val hiddenIds by viewModel.hiddenIds.collectAsState()
    val sort by viewModel.sortOption.collectAsState()
    val downloadedOnly by viewModel.showDownloadedOnly.collectAsState()
    val showHidden by viewModel.showHiddenItems.collectAsState()
    // The ViewModel's, so it outlives a rotation and empties when a folder change clears it
    val query by viewModel.filterQuery.collectAsState()
    val playback by playerViewModel.state.collectAsState()
    return LibraryUiState(
        title = title,
        canGoUp = canGoUp,
        items = items,
        loading = loading,
        error = error,
        downloadedIds = downloadedIds,
        hiddenIds = hiddenIds,
        query = query,
        sort = sort,
        downloadedOnly = downloadedOnly,
        showHidden = showHidden,
        itemMenu = itemMenu,
        nowPlaying = playback.currentTrack?.let { track ->
            NowPlayingBar(track.title, track.id.toUUIDOrNull()?.let(coverUrl), playback.playWhenReady)
        },
    )
}

/** Listening moves resume positions on, so once a book stops the list is loaded again. */
@Composable
private fun ReloadWhenPlaybackStops(viewModel: LibraryViewModel, playerViewModel: AudiobookPlayerViewModel) {
    LaunchedEffect(viewModel, playerViewModel) {
        var hadBook = false
        playerViewModel.state.collect { playback ->
            val hasBook = playback.currentTrack != null
            if (hadBook && !hasBook) viewModel.onPlaybackStopped()
            hadBook = hasBook
        }
    }
}

/** Tapping an item: a folder opens; a book plays, from where it was left. */
private class ItemOpener(
    private val viewModel: LibraryViewModel,
    private val scope: CoroutineScope,
    private val play: (LibraryViewModel.AudioQueue) -> Unit,
) {
    fun open(item: LibraryItem) {
        when (item) {
            // Always played whole, from its resume point; its tracks are in the player's list
            is LibraryItem.Album -> scope.launch { viewModel.fetchAlbumQueue(item.id)?.let(play) }
            is LibraryItem.BrowseFolder -> viewModel.openFolder(item.id, item.displayName)
            is LibraryItem.AudioFile -> scope.launch { play(viewModel.getAudioQueueWithResume(item.id)) }
            is LibraryItem.AudiobookBundle -> scope.launch { viewModel.fetchBundleQueue(item)?.let(play) }
        }
    }
}

/**
 * Holding an item: a folder opens; anything playable offers to download (or remove the download
 * of) its tracks, and to hide (or unhide) it.
 */
private class ItemMenus(
    private val context: Context,
    private val viewModel: LibraryViewModel,
    private val downloads: DownloadRequests,
    private val scope: CoroutineScope,
    private val show: (ItemMenu) -> Unit,
) {
    fun open(item: LibraryItem) {
        when (item) {
            is LibraryItem.BrowseFolder -> viewModel.openFolder(item.id, item.displayName)
            is LibraryItem.AudioFile -> show(item.id, item.displayName, listOf(item.id))
            is LibraryItem.AudiobookBundle -> show(item.id, item.displayName, item.trackIds)
            is LibraryItem.Album -> scope.launch {
                val trackIds = viewModel.fetchAlbumQueue(item.id)?.ids
                if (!trackIds.isNullOrEmpty()) show(item.id, item.displayName, trackIds)
            }
        }
    }

    /** A download toggle (only when [trackIds] isn't empty), then a hide toggle, which is always offered. */
    private fun show(itemId: UUID, title: String, trackIds: List<UUID>) {
        val actions = buildList {
            if (trackIds.isNotEmpty()) {
                val ids = trackIds.map(UUID::toString)
                if (allTracksDownloaded(trackIds, viewModel.downloadedIds.value)) {
                    add(ItemAction(context.getString(R.string.remove_download_action)) { downloads.remove(ids) })
                } else {
                    add(ItemAction(context.getString(R.string.download_action)) { downloads.download(ids) })
                }
            }
            val isHidden = itemId.toString() in viewModel.hiddenIds.value
            add(
                ItemAction(context.getString(if (isHidden) R.string.unhide_action else R.string.hide_action)) {
                    viewModel.toggleHidden(itemId)
                },
            )
        }
        show(ItemMenu(title, actions))
    }
}

private fun LibraryViewModel.AudioQueue.toPlayOptions() = PlayOptions(
    ids = ids,
    mediaSourceId = null,
    startIndex = startIndex,
    startPosition = startPosition,
    audioStreamIndex = null,
    subtitleStreamIndex = null,
    playFromDownloads = false,
    trackNames = trackNames,
)

/** The download menu offers "Remove Download" only once every one of [trackIds] is already downloaded. */
internal fun allTracksDownloaded(trackIds: List<UUID>, downloadedIds: Set<String>): Boolean =
    trackIds.isNotEmpty() && trackIds.all { it.toString() in downloadedIds }
