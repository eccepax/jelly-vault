package org.jellyfin.mobile.player.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.TimeBar
import coil3.load
import coil3.request.error
import coil3.request.fallback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.databinding.ExoPlayerControlViewBinding
import org.jellyfin.mobile.databinding.FragmentPlayerBinding
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.PlayerException
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.sleeptimer.SleepTimerDialogFragment
import org.jellyfin.mobile.player.sleeptimer.SleepTimerManager
import org.jellyfin.mobile.player.ui.playermenuhelper.PlayerMenuHelper
import org.jellyfin.mobile.utils.BackPressInterceptor
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.extensions.getParcelableCompat
import org.jellyfin.mobile.utils.extensions.keepScreenOn
import org.jellyfin.mobile.utils.toast
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import timber.log.Timber
import java.util.Locale

private const val TICKS_PER_MILLISECOND = 10_000L
private const val MILLISECONDS_PER_SECOND = 1000
private const val SECONDS_PER_HOUR = 3600
private const val SECONDS_PER_MINUTE = 60
private const val PROGRESS_UPDATE_INTERVAL_MS = 250L

// 250ms update interval * 8 ticks =~ 2 seconds between verbose progress-tick log lines.
private const val LOG_EVERY_N_TICKS = 8

@Suppress("TooManyFunctions")
class PlayerFragment : Fragment(), BackPressInterceptor {
    private val apiClient: ApiClient by inject()

    // Use activityViewModel() so the ViewModel (and ExoPlayer) outlives this Fragment.
    // This allows audio to keep playing when back is pressed, and the mini player bar on the
    // library screen to remain active and reopen the player without restarting playback.
    private val viewModel: AudiobookPlayerViewModel by activityViewModel()
    private var _playerBinding: FragmentPlayerBinding? = null
    private val playerBinding: FragmentPlayerBinding get() = _playerBinding!!
    private val playerView get() = playerBinding.playerView
    private val loadingIndicator: View get() = playerBinding.loadingIndicator
    private val albumCover get() = playerBinding.albumCover
    private var _playerControlsBinding: ExoPlayerControlViewBinding? = null
    private val playerControlsBinding: ExoPlayerControlViewBinding get() = _playerControlsBinding!!
    private val toolbar: Toolbar get() = playerBinding.toolbar
    private val durationText get() = playerControlsBinding.exoDuration
    private var playerMenus: PlayerMenus? = null
    private var playerListener: Player.Listener? = null
    private var progressUpdateJob: Job? = null
    private var mediaDurationMs: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.d("onCreate: viewModel instance = %s", viewModel)
        // Observe ViewModel — single registration point for the Player.Listener.
        viewModel.player.observe(this) { player ->
            Timber.d("player LiveData changed: player=%s", player)
            if (player == null) {
                parentFragmentManager.popBackStack()
            } else {
                // Connect PlayerView when the view already exists (no-op on first launch before onViewCreated).
                if (_playerBinding != null) {
                    playerView.player = player
                    updatePlayPauseButton(player.playWhenReady)
                }
                // Always (re-)register the listener so we never end up with zero or two copies.
                playerListener?.let { player.removeListener(it) }
                playerListener = object : Player.Listener {
                    // onIsPlayingChanged intentionally omitted: isPlaying flips false during HLS
                    // buffering even though playWhenReady stays true (user hasn't pressed pause).
                    // Driving the icon off isPlaying causes it to flicker to PLAY while buffering.
                    // The 250ms progress loop and onPlayWhenReadyChanged keep the icon correct.
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if (_playerControlsBinding != null) updatePlayPauseButton(playWhenReady)
                    }
                }
                player.addListener(playerListener!!)
            }
        }
        viewModel.error.observe(this) { message ->
            val safeMessage = message.ifEmpty { requireContext().getString(R.string.player_error_unspecific_exception) }
            requireContext().toast(safeMessage)
        }

        // Handle fragment arguments, extract playback options and start playback.
        // When opened via ResumePlayer (mini player tap) there are no args — the player is
        // already running and we just reconnect the UI; skip re-initialization entirely.
        //
        // savedInstanceState != null means this onCreate is a config-change recreation (e.g. screen
        // rotation — the manifest doesn't declare "orientation" in android:configChanges, so the
        // Activity/Fragment tree is torn down and rebuilt). The Fragment's `arguments` Bundle
        // survives that recreation just like savedInstanceState does, so without this guard the
        // *original* PlayOptions (whatever start position playback began at) would be replayed,
        // reloading the media source from that stale position and appearing to "restart the
        // chapter" — the ViewModel (and its ExoPlayer) already survived the recreation intact via
        // activityViewModel(), so there's nothing to re-initialize.
        lifecycleScope.launch {
            val context = requireContext()
            if (savedInstanceState != null) return@launch // Config-change recreation: player already active
            val playOptions = arguments?.getParcelableCompat<PlayOptions>(Constants.EXTRA_MEDIA_PLAY_OPTIONS)
                ?: return@launch // ResumePlayer path: ViewModel already has an active player
            when (viewModel.queueManager.initializePlaybackQueue(playOptions)) {
                is PlayerException.InvalidPlayOptions -> context.toast(R.string.player_error_invalid_play_options)
                is PlayerException.NetworkFailure -> context.toast(R.string.player_error_network_failure)
                is PlayerException.UnsupportedContent -> context.toast(R.string.player_error_unsupported_content)
                null -> Unit // success
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _playerBinding = FragmentPlayerBinding.inflate(inflater, container, false)
        // Important: search from mainContent, NOT from root. The root also contains PlayerView,
        // which internally inflates exo_player_control_view.xml (same layout name) and gives its
        // root the same id=player_controls. A root-level findViewByID finds that GONE internal
        // view first, resulting in a zero-size binding that never receives touches.
        _playerControlsBinding = ExoPlayerControlViewBinding.bind(playerBinding.mainContent.findViewById(R.id.player_controls))
        return playerBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupWindowInsets()

        // Enable marquee scrolling on the title
        playerBinding.nowPlayingTitle.isSelected = true

        // Handle toolbar back button
        toolbar.setNavigationOnClickListener { parentFragmentManager.popBackStack() }

        // Create playback menus
        playerMenus = PlayerMenus(this, playerBinding, playerControlsBinding)

        observePlayerState()
        observeCurrentMediaSource()
        setupOverflowMenu()
        setupPlaybackButtons()
        setupSeekBar()

        // Reconnect player if it already exists (handles orientation changes).
        // Listener management is handled exclusively by the observer in onCreate.
        viewModel.playerOrNull?.let { player ->
            playerView.player = player
            updatePlayPauseButton(player.playWhenReady)
        }

        startProgressUpdates()
    }

    private fun setupWindowInsets() {
        // Apply system bar insets so the seek bar isn't hidden behind gesture navigation
        ViewCompat.setOnApplyWindowInsetsListener(playerBinding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            playerBinding.mainContent.updatePadding(top = systemBars.top)
            playerControlsBinding.root.updatePadding(bottom = systemBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(playerBinding.root)
    }

    private fun observePlayerState() {
        // Keep loading spinner in sync with ExoPlayer state
        viewModel.playerState.observe(viewLifecycleOwner) { playerState ->
            val isPlaying = viewModel.playerOrNull?.isPlaying == true
            requireActivity().window.keepScreenOn = isPlaying
            playerBinding.loadingIndicator.isVisible = playerState == Player.STATE_BUFFERING
        }
    }

    private fun observeCurrentMediaSource() {
        // Update title + cover once the view exists (fixes "title never appears" when value emitted early)
        viewModel.queueManager.currentMediaSource.observe(viewLifecycleOwner) { mediaSource ->
            playerBinding.nowPlayingTitle.text = mediaSource.getName(requireContext())
            // Use Jellyfin runtime as duration fallback — HLS transcodes expose unknown duration to ExoPlayer
            mediaDurationMs = mediaSource.runTime.inWholeMilliseconds.takeIf { it > 0 }
                ?: mediaSource.item?.runTimeTicks?.let { ticks -> ticks / TICKS_PER_MILLISECOND }?.takeIf { it > 0 }
            val hasNext = viewModel.queueManager.hasNext()
            val hasPrevious = viewModel.queueManager.hasPrevious()
            playerMenus?.onQueueItemChanged(mediaSource, hasNext, hasPrevious)
            // Show tracklist option whenever there are multiple tracks in the queue
            toolbar.menu.findItem(R.id.action_tracklist)?.isVisible =
                viewModel.queueManager.queueSize > 1
            loadAlbumCover(mediaSource)
        }
    }

    private fun setupOverflowMenu() {
        // Overflow menu (3 dots) for secondary actions
        toolbar.menu.clear()
        toolbar.inflateMenu(R.menu.player_overflow_menu)
        toolbar.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.action_speed -> {
                    playerMenus?.showSpeedMenu(viewModel.playerOrNull?.playbackParameters?.speed ?: 1f)
                    true
                }
                R.id.action_sleep_timer -> {
                    showSleepTimerDialog()
                    true
                }
                R.id.action_tracklist -> {
                    val names = viewModel.queueManager.getTrackNames()
                    val currentIndex = viewModel.queueManager.currentIndex
                    playerMenus?.showTracklist(names, currentIndex) { index ->
                        viewModel.skipToQueueItem(index)
                    }
                    true
                }
                R.id.action_playback_info -> {
                    playerMenus?.togglePlaybackInfo()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupPlaybackButtons() {
        // Wire up all playback buttons directly here
        playerControlsBinding.exoPlayPause.setOnClickListener {
            val player = viewModel.playerOrNull
            Timber.d("play/pause clicked: player=%s playWhenReady=%s", player, player?.playWhenReady)
            val willPlay = player?.playWhenReady == true
            if (willPlay) viewModel.pause() else viewModel.play()
            updatePlayPauseButton(!willPlay)
        }
        playerControlsBinding.previousButton.setOnClickListener {
            Timber.d("rewind clicked")
            viewModel.rewind()
        }
        playerControlsBinding.nextButton.setOnClickListener {
            Timber.d("fastForward clicked")
            viewModel.fastForward()
        }
        playerControlsBinding.previousChapterButton.setOnClickListener {
            Timber.d("previousChapter clicked")
            viewModel.previousChapter()
        }
        playerControlsBinding.nextChapterButton.setOnClickListener {
            Timber.d("nextChapter clicked")
            viewModel.nextChapter()
        }
    }

    private fun setupSeekBar() {
        // Only onScrubStop matters here; the other two callbacks are intentional no-ops.
        playerControlsBinding.exoProgress.addListener(object : TimeBar.OnScrubListener {
            @Suppress("EmptyFunctionBlock")
            override fun onScrubStart(timeBar: TimeBar, position: Long) = Unit

            @Suppress("EmptyFunctionBlock")
            override fun onScrubMove(timeBar: TimeBar, position: Long) = Unit
            override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
                if (!canceled) {
                    // position is the absolute target (bar is set with absolute values);
                    // route through ViewModel so TRANSCODE streams restart from the right offset.
                    viewModel.seekToPosition(position)
                }
            }
        })
    }

    @UnstableApi
    private fun loadAlbumCover(mediaSource: org.jellyfin.mobile.player.source.JellyfinMediaSource) {
        val binding = _playerBinding ?: return
        val itemId = mediaSource.itemId
        val imageTag = mediaSource.item?.imageTags?.get(ImageType.PRIMARY)

        val imageUrl = apiClient.imageApi.getItemImageUrl(
            itemId = itemId,
            imageType = ImageType.PRIMARY,
            maxWidth = resources.getDimensionPixelSize(R.dimen.movie_thumbnail_list_size) * 2, // Larger for album cover
            maxHeight = resources.getDimensionPixelSize(R.dimen.movie_thumbnail_list_size) * 2,
            tag = imageTag,
        )

        binding.albumCover.load(imageUrl) {
            fallback(R.drawable.ic_audiobook_white_64)
            error(R.drawable.ic_audiobook_white_64)
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        val binding = _playerControlsBinding ?: return
        binding.exoPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause_black_42dp else R.drawable.ic_play_black_42dp,
        )
    }

    private fun updateProgress(position: Long, duration: Long) {
        val binding = _playerControlsBinding ?: return
        val localDurationMs = mediaDurationMs
        val safeDuration = when {
            duration != C.TIME_UNSET && duration > 0 -> duration
            localDurationMs != null -> localDurationMs
            else -> 0L
        }
        binding.exoProgress.setPosition(position.coerceAtLeast(0L))
        binding.exoProgress.setBufferedPosition(viewModel.adjustedBufferedPosition)
        binding.exoProgress.setDuration(safeDuration)
        binding.exoPosition.text = formatTime(position)
        binding.exoDuration.text = if (safeDuration > 0) formatTime(safeDuration) else "--:--"
    }

    internal fun formatTime(timeMs: Long): String {
        val totalSeconds = (timeMs / MILLISECONDS_PER_SECOND).toInt()
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = lifecycleScope.launch {
            var tickCount = 0
            while (true) {
                delay(PROGRESS_UPDATE_INTERVAL_MS) // Update ~4x/sec; lighter than 100ms
                tickCount++
                if (_playerControlsBinding != null) {
                    val player = viewModel.playerOrNull
                    if (player != null) {
                        val adjustedPos = viewModel.adjustedCurrentPosition
                        if (tickCount % LOG_EVERY_N_TICKS == 0) { // avoid log spam
                            Timber.d(
                                "progress tick #%d: pos=%d dur=%d playing=%s playWhenReady=%s state=%d",
                                tickCount,
                                adjustedPos,
                                player.duration,
                                player.isPlaying,
                                player.playWhenReady,
                                player.playbackState,
                            )
                        }
                        updateProgress(adjustedPos, player.duration)
                        updatePlayPauseButton(player.playWhenReady)
                    } else {
                        if (tickCount % LOG_EVERY_N_TICKS == 0) {
                            Timber.w("progress tick #%d: playerOrNull is null", tickCount)
                        }
                    }
                }
            }
        }
    }

    private fun showSleepTimerDialog() {
        // Register before showing so the result is never missed, even after recreation.
        parentFragmentManager.setFragmentResultListener(
            SleepTimerDialogFragment.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            val durationName = bundle.getString(SleepTimerDialogFragment.KEY_DURATION)
            val endOfChapter = bundle.getBoolean(SleepTimerDialogFragment.KEY_END_OF_CHAPTER, false)
            val turnOff = bundle.getBoolean(SleepTimerDialogFragment.KEY_TURN_OFF, false)
            when {
                turnOff -> {
                    viewModel.cancelSleepTimer()
                    requireContext().toast(R.string.sleep_timer_off)
                }
                endOfChapter -> {
                    viewModel.startSleepTimerEndOfChapter()
                    requireContext().toast(R.string.sleep_timer_set_end_of_chapter)
                }
                durationName != null -> {
                    val duration = SleepTimerManager.SleepTimerDuration.valueOf(durationName)
                    viewModel.startSleepTimer(duration)
                    val durationText = when (duration) {
                        SleepTimerManager.SleepTimerDuration.FIFTEEN_MINUTES -> getString(R.string.sleep_timer_15_min)
                        SleepTimerManager.SleepTimerDuration.THIRTY_MINUTES -> getString(R.string.sleep_timer_30_min)
                        SleepTimerManager.SleepTimerDuration.FORTY_FIVE_MINUTES -> getString(
                            R.string.sleep_timer_45_min,
                        )
                        SleepTimerManager.SleepTimerDuration.SIXTY_MINUTES -> getString(R.string.sleep_timer_60_min)
                    }
                    requireContext().toast(getString(R.string.sleep_timer_set, durationText))
                }
            }
        }
        SleepTimerDialogFragment.newInstance(timerActive = viewModel.isSleepTimerActive)
            .show(parentFragmentManager, "SleepTimerDialog")
    }

    override fun onPause() {
        super.onPause()
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    override fun onResume() {
        super.onResume()
        // Always restart: a book still loading when the screen was left has no player yet, and the
        // loop waits for one; skipping it here left the progress frozen once playback started.
        startProgressUpdates()
    }

    /**
     * @param callback called if track selection was successful and UI needs to be updated
     */
    fun onAudioTrackSelected(index: Int, callback: TrackSelectionCallback): Job = lifecycleScope.launch {
        if (viewModel.trackSelectionHelper.selectAudioTrack(index)) {
            callback.onTrackSelected(true)
        }
    }

    /**
     * @return true if the playback speed was changed
     */
    fun onSpeedSelected(speed: Float): Boolean {
        return viewModel.setPlaybackSpeed(speed)
    }

    fun onPressSpeedUp(isPressing: Boolean): Boolean {
        return viewModel.setPressSpeedUp(isPressing, Constants.HOLD_SPEEDUP_MULTIPLIER)
    }

    fun onSkipToPrevious() {
        viewModel.skipToPrevious()
    }

    fun onSkipToNext() {
        viewModel.skipToNext()
    }

    fun onSkipMediaSegment(mediaSegmentDto: MediaSegmentDto?) {
        viewModel.skipMediaSegment(mediaSegmentDto)
    }

    override fun onDestroyView() {
        super.onDestroyView()

        progressUpdateJob?.cancel()
        progressUpdateJob = null

        // Prevent leaking the old Fragment view via ViewModel -> PlayerMenuHelper -> view references
        viewModel.setPlayerMenuHelper(null)

        viewModel.playerOrNull?.let { player ->
            playerListener?.let { player.removeListener(it) }
        }
        playerListener = null

        if (_playerBinding != null) {
            playerView.player = null
        }

        _playerBinding = null
        _playerControlsBinding = null
        playerMenus = null
    }

    fun setPlayerMenuHelper(menuHelper: PlayerMenuHelper?) {
        viewModel.setPlayerMenuHelper(menuHelper)
    }
}
