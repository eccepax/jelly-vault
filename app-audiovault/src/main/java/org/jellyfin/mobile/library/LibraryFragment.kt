package org.jellyfin.mobile.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.utils.BackPressInterceptor
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.UUID

/**
 * Fragment for browsing and playing audiobooks/audio content from Jellyfin server
 */
class LibraryFragment : Fragment(), BackPressInterceptor {
    private val viewModel: LibraryViewModel by viewModel()
    private val playerViewModel: AudiobookPlayerViewModel by activityViewModel()
    private val activityEventHandler: ActivityEventHandler by inject()
    private val apiClient: ApiClient by inject()

    private var _binding: FragmentComposeBinding? = null
    private val binding get() = _binding!!

    private var itemMenu by mutableStateOf<ItemMenu?>(null)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentComposeBinding.inflate(inflater, container, false)
        return binding.composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.requestApplyInsets(binding.composeView)
        val coverSize = resources.getDimensionPixelSize(R.dimen.movie_thumbnail_list_size)
        val actions = LibraryActions(
            onUp = { viewModel.goUp() },
            onQueryChange = viewModel::setFilterQuery,
            onSort = viewModel::setSortOption,
            onDownloadedOnly = { viewModel.showDownloadedOnly.value = it },
            onShowHidden = { viewModel.showHiddenItems.value = it },
            onSettings = { activityEventHandler.emit(ActivityEvent.OpenSettings) },
            onRefresh = { viewModel.refresh(force = true) },
            onOpen = ::onItemClick,
            onLongPress = ::onItemLongClick,
            onDismissMenu = { itemMenu = null },
            onOpenPlayer = { activityEventHandler.emit(ActivityEvent.ResumePlayer) },
            onPlayPause = playerViewModel::togglePlayPause,
            onStop = playerViewModel::stop,
            coverUrl = { id -> coverUrl(id, coverSize) },
        )
        binding.composeView.setContent {
            AppTheme(VaultBrand.AudioVault) {
                LibraryScreen(collectState(coverSize), actions)
            }
        }
        refreshWhenPlaybackStops()
    }

    @Composable
    private fun collectState(coverSize: Int): LibraryUiState {
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
        val nowPlaying = playback.currentTrack
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
            nowPlaying = nowPlaying?.let { track ->
                NowPlayingBar(
                    track.title,
                    track.id.toUUIDOrNull()?.let { coverUrl(it, coverSize) },
                    playback.playWhenReady,
                )
            },
        )
    }

    private fun coverUrl(itemId: UUID, size: Int): String? = apiClient.imageApi.getItemImageUrl(
        itemId = itemId,
        imageType = ImageType.PRIMARY,
        maxWidth = size,
        maxHeight = size,
    )

    /** Listening moves resume positions on, so once a book stops the list is loaded again. */
    private fun refreshWhenPlaybackStops() {
        var hadBook = false
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerViewModel.state.collect { playback ->
                    val hasBook = playback.currentTrack != null
                    if (hadBook && !hasBook) viewModel.onPlaybackStopped()
                    hadBook = hasBook
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkAndReloadIfSettingChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onInterceptBackPressed(): Boolean = viewModel.goUp()

    private fun onItemClick(item: LibraryItem) {
        when (item) {
            is LibraryItem.Album -> {
                // MusicAlbum: always play directly with resume position.
                // Individual tracks are accessible from the player tracklist, not the library browser.
                viewLifecycleOwner.lifecycleScope.launch {
                    val queue = viewModel.fetchAlbumQueue(item.id)
                    if (queue != null) launchPlayer(queue)
                }
            }
            is LibraryItem.BrowseFolder -> {
                viewModel.openFolder(item.id, item.displayName)
            }
            is LibraryItem.AudioFile -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    launchPlayer(viewModel.getAudioQueueWithResume(item.id))
                }
            }
            is LibraryItem.AudiobookBundle -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    val queue = viewModel.fetchBundleQueue(item)
                    if (queue != null) launchPlayer(queue)
                }
            }
        }
    }

    /**
     * Long-press on a BrowseFolder navigates into it (unchanged). Long-press on any playable item
     * offers to download (or remove the download of) its track(s), and to hide (or unhide) the item.
     */
    private fun onItemLongClick(item: LibraryItem) {
        when (item) {
            is LibraryItem.BrowseFolder -> viewModel.openFolder(item.id, item.displayName)
            is LibraryItem.AudioFile -> showItemMenu(item.id, item.displayName, listOf(item.id))
            is LibraryItem.AudiobookBundle -> showItemMenu(item.id, item.displayName, item.trackIds)
            is LibraryItem.Album -> viewLifecycleOwner.lifecycleScope.launch {
                val trackIds = viewModel.fetchAlbumQueue(item.id)?.ids
                if (!trackIds.isNullOrEmpty()) showItemMenu(item.id, item.displayName, trackIds)
            }
        }
    }

    /**
     * Opens the long-press menu for [itemId]: a download toggle (only when [trackIds] isn't empty)
     * followed by a hide toggle, which is always offered.
     */
    private fun showItemMenu(itemId: UUID, title: String, trackIds: List<UUID>) {
        val actions = buildList {
            if (trackIds.isNotEmpty()) {
                val ids = trackIds.map(UUID::toString)
                if (allTracksDownloaded(trackIds, viewModel.downloadedIds.value)) {
                    add(
                        ItemAction(getString(R.string.remove_download_action)) {
                            activityEventHandler.emit(ActivityEvent.RemoveTracks(ids))
                        },
                    )
                } else {
                    add(
                        ItemAction(getString(R.string.download_action)) {
                            activityEventHandler.emit(ActivityEvent.DownloadTracks(ids))
                        },
                    )
                }
            }
            val isHidden = itemId.toString() in viewModel.hiddenIds.value
            add(
                ItemAction(getString(if (isHidden) R.string.unhide_action else R.string.hide_action)) {
                    viewModel.toggleHidden(itemId)
                },
            )
        }
        itemMenu = ItemMenu(title, actions)
    }

    private fun launchPlayer(queue: LibraryViewModel.AudioQueue) {
        val playOptions = PlayOptions(
            ids = queue.ids,
            mediaSourceId = null,
            startIndex = queue.startIndex,
            startPosition = queue.startPosition,
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            playFromDownloads = false,
            trackNames = queue.trackNames,
        )
        activityEventHandler.emit(ActivityEvent.LaunchNativePlayer(playOptions))
    }
}

/** The download menu offers "Remove Download" only once every one of [trackIds] is already downloaded. */
internal fun allTracksDownloaded(trackIds: List<UUID>, downloadedIds: Set<String>): Boolean =
    trackIds.isNotEmpty() && trackIds.all { it.toString() in downloadedIds }
