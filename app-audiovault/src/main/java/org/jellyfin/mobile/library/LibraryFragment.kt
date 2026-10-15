package org.jellyfin.mobile.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import coil3.load
import coil3.request.error
import coil3.request.fallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.databinding.FragmentLibraryBinding
import org.jellyfin.mobile.events.ActivityEvent
import org.jellyfin.mobile.events.ActivityEventHandler
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.utils.BackPressInterceptor
import org.jellyfin.mobile.utils.applyWindowInsetsAsMargins
import org.jellyfin.mobile.utils.withThemedContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.ImageType
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.util.UUID
import org.jellyfin.mobile.common.R as CommonR

/**
 * Fragment for browsing and playing audiobooks/audio content from Jellyfin server
 */
class LibraryFragment : Fragment(), BackPressInterceptor {
    private val viewModel: LibraryViewModel by viewModel()
    private val playerViewModel: AudiobookPlayerViewModel by activityViewModel()
    private val activityEventHandler: ActivityEventHandler by inject()
    private val apiClient: ApiClient by inject()
    private lateinit var adapter: LibraryAdapter
    private var _binding: FragmentLibraryBinding? = null
    private val binding: FragmentLibraryBinding get() = _binding!!
    private var miniPlayerListener: Player.Listener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val localInflater = inflater.withThemedContext(requireContext(), CommonR.style.AppTheme_Settings)
        _binding = FragmentLibraryBinding.inflate(localInflater, container, false)
        binding.root.applyWindowInsetsAsMargins()

        setupToolbar()
        setupRecyclerView()
        observeViewModel()
        setupMiniPlayer()

        return binding.root
    }

    private fun setupToolbar() {
        binding.toolbar.setTitle(R.string.library)
        // Drive the Toolbar standalone — do NOT call setSupportActionBar, which hands
        // menu management to the Activity's ActionBar system and causes it to wipe the menu.
        binding.toolbar.navigationIcon = null
        binding.toolbar.inflateMenu(R.menu.library_toolbar_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    activityEventHandler.emit(ActivityEvent.OpenSettings)
                    true
                }
                R.id.action_filter_downloaded -> {
                    viewModel.showDownloadedOnly.value = !viewModel.showDownloadedOnly.value
                    item.isChecked = viewModel.showDownloadedOnly.value
                    true
                }
                R.id.action_show_hidden -> {
                    viewModel.showHiddenItems.value = !viewModel.showHiddenItems.value
                    item.isChecked = viewModel.showHiddenItems.value
                    true
                }
                R.id.sort_title -> {
                    viewModel.setSortOption(SortOption.TITLE)
                    true
                }
                R.id.sort_author -> {
                    viewModel.setSortOption(SortOption.AUTHOR)
                    true
                }
                R.id.sort_date_added -> {
                    viewModel.setSortOption(SortOption.DATE_ADDED)
                    true
                }
                R.id.sort_last_played -> {
                    viewModel.setSortOption(SortOption.LAST_PLAYED)
                    true
                }
                else -> false
            }
        }

        // Wire the SearchView to the ViewModel filter
        val searchItem = binding.toolbar.menu.findItem(R.id.action_search)
        (searchItem?.actionView as? SearchView)?.apply {
            queryHint = getString(R.string.search_hint)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean = true
                override fun onQueryTextChange(newText: String): Boolean {
                    viewModel.setFilterQuery(newText)
                    return true
                }
            })
        }
        searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean = true
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.setFilterQuery("")
                return true
            }
        })
    }

    private fun setupRecyclerView() {
        adapter = LibraryAdapter(
            apiClient = apiClient,
            onItemClick = ::onItemClick,
            onItemLongClick = ::onItemLongClick,
        )
        binding.recyclerView.adapter = adapter

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refresh(force = true)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                observeListState()
                observeToolbarFilterState()
            }
        }
    }

    private fun CoroutineScope.observeListState() {
        launch {
            viewModel.items.collect { items -> adapter.submitList(items) }
        }
        launch {
            viewModel.title.collect { title -> binding.toolbar.title = title }
        }
        launch {
            viewModel.canGoUp.collect { canGoUp ->
                binding.toolbar.navigationIcon = if (canGoUp) {
                    AppCompatResources.getDrawable(
                        requireContext(),
                        androidx.appcompat.R.drawable.abc_ic_ab_back_material,
                    )
                } else {
                    null
                }
                binding.toolbar.setNavigationOnClickListener(
                    if (canGoUp) View.OnClickListener { viewModel.goUp() } else null,
                )
            }
        }
        launch {
            viewModel.isLoading.collect { isLoading ->
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                binding.swipeRefreshLayout.isRefreshing = isLoading
            }
        }
        launch {
            viewModel.error.collect { error ->
                if (error != null) {
                    binding.textViewError.text = error
                    binding.textViewError.visibility = View.VISIBLE
                } else {
                    binding.textViewError.visibility = View.GONE
                }
            }
        }
    }

    private fun CoroutineScope.observeToolbarFilterState() {
        launch {
            viewModel.sortOption.collect { sortOption ->
                val checkedId = when (sortOption) {
                    SortOption.TITLE -> R.id.sort_title
                    SortOption.AUTHOR -> R.id.sort_author
                    SortOption.DATE_ADDED -> R.id.sort_date_added
                    SortOption.LAST_PLAYED -> R.id.sort_last_played
                }
                binding.toolbar.menu.findItem(checkedId)?.isChecked = true
            }
        }
        launch {
            viewModel.showDownloadedOnly.collect { downloadedOnly ->
                binding.toolbar.menu.findItem(R.id.action_filter_downloaded)?.isChecked = downloadedOnly
            }
        }
        launch {
            viewModel.downloadedIds.collect { ids -> adapter.updateDownloadedIds(ids) }
        }
        launch {
            viewModel.showHiddenItems.collect { showHidden ->
                binding.toolbar.menu.findItem(R.id.action_show_hidden)?.isChecked = showHidden
            }
        }
        launch {
            viewModel.hiddenIds.collect { ids -> adapter.updateHiddenIds(ids) }
        }
    }

    private fun setupMiniPlayer() {
        // Mini player: show when audio is active, tap to reopen player
        binding.miniPlayer.setOnClickListener {
            activityEventHandler.emit(ActivityEvent.ResumePlayer)
        }
        binding.miniPlayerPlayPause.setOnClickListener {
            val player = playerViewModel.playerOrNull ?: return@setOnClickListener
            if (player.playWhenReady) playerViewModel.pause() else playerViewModel.play()
        }
        binding.miniPlayerStop.setOnClickListener {
            playerViewModel.stop()
        }

        var hadActivePlayer = false
        playerViewModel.player.observe(viewLifecycleOwner) { player ->
            val justStopped = hadActivePlayer && player == null
            hadActivePlayer = player != null
            if (justStopped) viewModel.onPlaybackStopped()
            binding.miniPlayer.isVisible = player != null
            // Detach old listener before attaching a new one
            miniPlayerListener?.let { playerViewModel.playerOrNull?.removeListener(it) }
            miniPlayerListener = null
            if (player != null) {
                updateMiniPlayerPlayPause(player.playWhenReady)
                val listener = object : Player.Listener {
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if (_binding != null) updateMiniPlayerPlayPause(playWhenReady)
                    }
                }
                player.addListener(listener)
                miniPlayerListener = listener
            }
        }

        playerViewModel.queueManager.currentMediaSource.observe(viewLifecycleOwner) { mediaSource ->
            binding.miniPlayerTitle.text = mediaSource.getName(requireContext())
            val size = resources.getDimensionPixelSize(R.dimen.movie_thumbnail_list_size)
            val imageUrl = apiClient.imageApi.getItemImageUrl(
                itemId = mediaSource.itemId,
                imageType = ImageType.PRIMARY,
                maxWidth = size,
                maxHeight = size,
            )
            binding.miniPlayerCover.load(imageUrl) {
                fallback(R.drawable.ic_music_note_white_24dp)
                error(R.drawable.ic_music_note_white_24dp)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkAndReloadIfSettingChanged()
    }

    private fun updateMiniPlayerPlayPause(playWhenReady: Boolean) {
        _binding?.miniPlayerPlayPause?.setImageResource(
            if (playWhenReady) R.drawable.ic_pause_black_42dp else R.drawable.ic_play_black_42dp,
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        miniPlayerListener?.let { playerViewModel.playerOrNull?.removeListener(it) }
        miniPlayerListener = null
        _binding = null
    }

    override fun onInterceptBackPressed(): Boolean = viewModel.goUp()

    internal fun onItemClick(item: LibraryItem) {
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
    internal fun onItemLongClick(item: LibraryItem): Boolean {
        when (item) {
            is LibraryItem.BrowseFolder -> viewModel.openFolder(item.id, item.displayName)
            is LibraryItem.AudioFile -> showItemActionMenu(item.id, item.displayName, listOf(item.id))
            is LibraryItem.AudiobookBundle -> showItemActionMenu(item.id, item.displayName, item.trackIds)
            is LibraryItem.Album -> viewLifecycleOwner.lifecycleScope.launch {
                val trackIds = viewModel.fetchAlbumQueue(item.id)?.ids
                if (!trackIds.isNullOrEmpty()) showItemActionMenu(item.id, item.displayName, trackIds)
            }
        }
        return true
    }

    /**
     * Builds the long-press action list for [itemId]: a download toggle (only when [trackIds]
     * isn't empty) followed by a hide toggle, which is always offered.
     */
    private fun showItemActionMenu(itemId: UUID, title: String, trackIds: List<UUID>) {
        val actions = buildList {
            if (trackIds.isNotEmpty()) {
                val ids = trackIds.map(UUID::toString)
                if (allTracksDownloaded(trackIds, viewModel.downloadedIds.value)) {
                    add(
                        getString(R.string.remove_download_action) to { activityEventHandler.emit(ActivityEvent.RemoveTracks(ids)) },
                    )
                } else {
                    add(
                        getString(R.string.download_action) to { activityEventHandler.emit(ActivityEvent.DownloadTracks(ids)) },
                    )
                }
            }
            val isHidden = itemId.toString() in viewModel.hiddenIds.value
            val label = getString(if (isHidden) R.string.unhide_action else R.string.hide_action)
            add(label to { viewModel.toggleHidden(itemId) })
        }
        android.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setItems(actions.map { it.first }.toTypedArray()) { _, index -> actions[index].second() }
            .show()
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
