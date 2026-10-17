package org.jellyfin.mobile.player.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.Player
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.PlayerException
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.sleeptimer.SleepTimerManager.SleepTimerDuration
import org.jellyfin.mobile.player.source.JellyfinMediaSource
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.extensions.getParcelableCompat
import org.jellyfin.mobile.utils.extensions.keepScreenOn
import org.jellyfin.mobile.utils.toast
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStream
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel
import timber.log.Timber

private const val TICKS_PER_MILLISECOND = 10_000L
private const val PROGRESS_UPDATE_INTERVAL_MS = 250L
private const val MAX_AUDIO_STREAMS_DISPLAY = 5

class PlayerFragment : Fragment() {
    private val apiClient: ApiClient by inject()
    private val appPreferences: AudioVaultAppPreferences by inject()

    // Use activityViewModel() so the ViewModel (and ExoPlayer) outlives this Fragment.
    // This allows audio to keep playing when back is pressed, and the mini player bar on the
    // library screen to remain active and reopen the player without restarting playback.
    private val viewModel: AudiobookPlayerViewModel by activityViewModel()

    private var _binding: FragmentComposeBinding? = null
    private val binding get() = _binding!!

    /** All of what the screen shows, but the skip segment, which the ViewModel publishes itself. */
    private var state by mutableStateOf(PlayerUiState())
    private var progressUpdateJob: Job? = null
    private var mediaDurationMs: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.player.observe(this) { player ->
            Timber.d("player LiveData changed: player=%s", player)
            if (player == null) parentFragmentManager.popBackStack()
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
        _binding = FragmentComposeBinding.inflate(inflater, container, false)
        return binding.composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.requestApplyInsets(binding.composeView)
        val actions = PlayerActions(
            onBack = { parentFragmentManager.popBackStack() },
            onPlayPause = ::togglePlayPause,
            onRewind = viewModel::rewind,
            onFastForward = viewModel::fastForward,
            onHoldSpeedUp = { holding -> viewModel.setPressSpeedUp(holding, Constants.HOLD_SPEEDUP_MULTIPLIER) },
            onPreviousChapter = viewModel::previousChapter,
            onNextChapter = viewModel::nextChapter,
            // Through the ViewModel, so transcoded streams restart from the right offset
            onSeek = viewModel::seekToPosition,
            onSpeed = { speed -> if (viewModel.setPlaybackSpeed(speed)) state = state.copy(speed = speed) },
            onSleepTimer = ::chooseSleepTimer,
            onTrack = viewModel::skipToQueueItem,
            onSkipSegment = { viewModel.skipMediaSegment(viewModel.skipSegment.value) },
        )
        binding.composeView.setContent {
            AppTheme(VaultBrand.AudioVault) {
                val skipSegment by viewModel.skipSegment.collectAsState()
                PlayerScreen(state.copy(skipSegment = skipSegment != null), actions)
            }
        }
        // As things stand now, not 250ms from now when the progress loop first looks
        state = state.copy(
            holdToSpeedUp = appPreferences.exoPlayerAllowPressSpeedUp,
            isPlaying = viewModel.playerOrNull?.playWhenReady == true,
            sleepTimerActive = viewModel.isSleepTimerActive,
        )

        viewModel.playerState.observe(viewLifecycleOwner) { playerState ->
            requireActivity().window.keepScreenOn = viewModel.playerOrNull?.isPlaying == true
            state = state.copy(buffering = playerState == Player.STATE_BUFFERING)
        }
        viewModel.queueManager.currentMediaSource.observe(viewLifecycleOwner, ::showMediaSource)
    }

    private fun togglePlayPause() {
        val player = viewModel.playerOrNull
        val willPlay = player?.playWhenReady == true
        if (willPlay) viewModel.pause() else viewModel.play()
        state = state.copy(isPlaying = !willPlay)
    }

    private fun showMediaSource(mediaSource: JellyfinMediaSource) {
        // Jellyfin's runtime stands in for the length: transcoded streams don't tell ExoPlayer theirs
        mediaDurationMs = mediaSource.runTime.inWholeMilliseconds.takeIf { it > 0 }
            ?: mediaSource.item?.runTimeTicks?.let { ticks -> ticks / TICKS_PER_MILLISECOND }?.takeIf { it > 0 }
        val chapters = mediaSource.item?.chapters.orEmpty()
        val runTimeTicks = mediaSource.item?.runTimeTicks
        val queue = viewModel.queueManager
        val coverSize = resources.getDimensionPixelSize(R.dimen.movie_thumbnail_list_size) * 2
        state = state.copy(
            title = mediaSource.getName(requireContext()),
            coverUrl = apiClient.imageApi.getItemImageUrl(
                itemId = mediaSource.itemId,
                imageType = ImageType.PRIMARY,
                maxWidth = coverSize,
                maxHeight = coverSize,
                tag = mediaSource.item?.imageTags?.get(ImageType.PRIMARY),
            ),
            chapterStarts = if (runTimeTicks != null && runTimeTicks > 0) {
                chapters.map { (it.startPositionTicks.toFloat() / runTimeTicks).coerceIn(0f, 1f) }
            } else {
                emptyList()
            },
            // Chapters, or other tracks in the queue, to move between
            showChapterButtons = chapters.isNotEmpty() || queue.hasPrevious() || queue.hasNext(),
            tracks = queue.getTrackNames(),
            currentTrack = queue.currentIndex,
            playbackInfo = playbackInfoOf(mediaSource),
        )
    }

    private fun playbackInfoOf(mediaSource: JellyfinMediaSource): String {
        val playMethod = getString(R.string.playback_info_play_method, mediaSource.playMethod)
        val audioStreams = streamsInfo(mediaSource.audioStreams, R.string.playback_info_audio_streams) { stream ->
            stream.language?.let { lang -> " ($lang)" }.orEmpty()
        }
        return listOf(playMethod, audioStreams).joinToString("\n\n")
    }

    private fun streamsInfo(streams: List<MediaStream>, @StringRes prefix: Int, suffix: (MediaStream) -> String) =
        streams.joinToString(
            "\n",
            "${getString(prefix)}:\n",
            limit = MAX_AUDIO_STREAMS_DISPLAY,
            truncated = getString(R.string.playback_info_and_x_more, streams.size - MAX_AUDIO_STREAMS_DISPLAY),
        ) { stream ->
            val title = stream.displayTitle?.takeUnless(String::isEmpty)
                ?: getString(R.string.playback_info_stream_unknown_title)
            "- $title${suffix(stream)}"
        }

    private fun chooseSleepTimer(choice: SleepTimerChoice) {
        when (choice) {
            SleepTimerChoice.TurnOff -> {
                viewModel.cancelSleepTimer()
                requireContext().toast(R.string.sleep_timer_off)
            }
            SleepTimerChoice.EndOfChapter -> {
                viewModel.startSleepTimerEndOfChapter()
                requireContext().toast(R.string.sleep_timer_set_end_of_chapter)
            }
            is SleepTimerChoice.For -> {
                viewModel.startSleepTimer(choice.duration)
                val length = getString(
                    when (choice.duration) {
                        SleepTimerDuration.FIFTEEN_MINUTES -> R.string.sleep_timer_15_min
                        SleepTimerDuration.THIRTY_MINUTES -> R.string.sleep_timer_30_min
                        SleepTimerDuration.FORTY_FIVE_MINUTES -> R.string.sleep_timer_45_min
                        SleepTimerDuration.SIXTY_MINUTES -> R.string.sleep_timer_60_min
                    },
                )
                requireContext().toast(getString(R.string.sleep_timer_set, length))
            }
        }
        state = state.copy(sleepTimerActive = viewModel.isSleepTimerActive)
    }

    /** Follows the player about 4 times a second; it has no event for the position moving. */
    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = lifecycleScope.launch {
            while (true) {
                delay(PROGRESS_UPDATE_INTERVAL_MS)
                val player = viewModel.playerOrNull ?: continue
                val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: mediaDurationMs ?: 0L
                state = state.copy(
                    // The icon follows playWhenReady, not isPlaying, which flickers false while buffering
                    isPlaying = player.playWhenReady,
                    positionMs = viewModel.adjustedCurrentPosition.coerceAtLeast(0L),
                    durationMs = duration,
                    // The speed chosen, not the player's, which is faster while fast-forward is held
                    speed = viewModel.playbackSpeed.takeIf { it > 0f } ?: state.speed,
                    sleepTimerActive = viewModel.isSleepTimerActive,
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        progressUpdateJob?.cancel()
        progressUpdateJob = null
    }

    override fun onResume() {
        super.onResume()
        state = state.copy(holdToSpeedUp = appPreferences.exoPlayerAllowPressSpeedUp)
        // Always restart: a book still loading when the screen was left has no player yet, and the
        // loop waits for one; skipping it here left the progress frozen once playback started.
        startProgressUpdates()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressUpdateJob?.cancel()
        progressUpdateJob = null
        _binding = null
    }
}
