package org.jellyfin.mobile.player.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.playback.SleepTimerState
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.databinding.FragmentComposeBinding
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.PlayerException
import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.mobile.player.playback.AudiobookPlaybackState
import org.jellyfin.mobile.utils.Constants
import org.jellyfin.mobile.utils.extensions.getParcelableCompat
import org.jellyfin.mobile.utils.extensions.keepScreenOn
import org.jellyfin.mobile.utils.toast
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.activityViewModel

private const val PROGRESS_UPDATE_INTERVAL_MS = 250L

class PlayerFragment : Fragment() {
    private val appPreferences: AudioVaultAppPreferences by inject()

    // The book plays in the playback service; this only shows it, and goes when it's left.
    private val viewModel: AudiobookPlayerViewModel by activityViewModel()

    private var _binding: FragmentComposeBinding? = null
    private val binding get() = _binding!!

    /** All of what the screen shows, but the skip segment, which the view model publishes itself. */
    private var state by mutableStateOf(PlayerUiState())
    private var progressUpdateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Opened from the mini player there are no PlayOptions: the book is already playing. And a
        // recreation (a rotation) keeps the Fragment's arguments, which mustn't start the book again
        // from where it first started.
        if (savedInstanceState != null) return
        val playOptions = arguments?.getParcelableCompat<PlayOptions>(Constants.EXTRA_MEDIA_PLAY_OPTIONS) ?: return
        lifecycleScope.launch {
            val context = requireContext()
            when (viewModel.start(playOptions)) {
                is PlayerException.InvalidPlayOptions -> context.toast(R.string.player_error_invalid_play_options)
                is PlayerException.NetworkFailure -> context.toast(R.string.player_error_network_failure)
                is PlayerException.UnsupportedContent -> context.toast(R.string.player_error_unsupported_content)
                null -> return@launch // success
            }
            // Nothing to show: back to where the book was picked
            if (viewModel.state.value.currentTrack == null && !parentFragmentManager.isStateSaved) {
                parentFragmentManager.popBackStack()
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
            onPlayPause = viewModel::togglePlayPause,
            onRewind = viewModel::rewind,
            onFastForward = viewModel::fastForward,
            onHoldSpeedUp = viewModel::holdSpeedUp,
            onPreviousChapter = viewModel::previousChapter,
            onNextChapter = viewModel::nextChapter,
            onSeek = viewModel::seekTo,
            onSpeed = viewModel::setSpeed,
            onSleepTimer = ::chooseSleepTimer,
            onTrack = viewModel::skipToTrack,
            onSkipSegment = viewModel::skipOfferedSegment,
        )
        binding.composeView.setContent {
            AppTheme(VaultBrand.AudioVault) {
                val skipSegment by viewModel.skipSegment.collectAsState()
                PlayerScreen(state.copy(skipSegment = skipSegment != null), actions)
            }
        }
        followPlayback()
    }

    /** Shows the book playing; leaves when it stops (the mini player's stop, the last track ending). */
    private fun followPlayback() {
        var hadBook = false
        var lastError: String? = null
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { playback ->
                    val track = playback.currentTrack
                    if (track == null) {
                        if (hadBook) parentFragmentManager.popBackStack()
                        return@collect
                    }
                    hadBook = true
                    requireActivity().window.keepScreenOn = playback.isPlaying
                    if (playback.error != null && playback.error != lastError) {
                        requireContext().toast(playback.error)
                    }
                    lastError = playback.error
                    show(playback)
                }
            }
        }
    }

    private fun show(playback: AudiobookPlaybackState) {
        val track = playback.currentTrack ?: return
        val chapterStarts = if (track.durationMs > 0) {
            track.chapters.map { (it.startMs.toFloat() / track.durationMs).coerceIn(0f, 1f) }
        } else {
            emptyList()
        }
        state = state.copy(
            title = track.title,
            coverUrl = track.coverUrl,
            isPlaying = playback.playWhenReady,
            buffering = playback.isBuffering,
            chapterStarts = chapterStarts,
            // Chapters, or other tracks in the queue, to move between
            showChapterButtons = track.chapters.isNotEmpty() || playback.tracks.size > 1,
            tracks = playback.tracks.map { it.title },
            currentTrack = playback.currentIndex,
            playbackInfo = getString(if (track.fromDevice) R.string.playback_info_from_device else R.string.playback_info_streamed),
        )
    }

    private fun chooseSleepTimer(choice: SleepTimerChoice) {
        when (choice) {
            SleepTimerChoice.TurnOff -> {
                viewModel.cancelSleepTimer()
                requireContext().toast(R.string.sleep_timer_off)
            }
            SleepTimerChoice.EndOfChapter -> {
                viewModel.sleepAtEndOfChapter()
                requireContext().toast(R.string.sleep_timer_set_end_of_chapter)
            }
            is SleepTimerChoice.For -> {
                viewModel.startSleepTimer(choice.duration.minutes)
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
        state = state.copy(sleepTimerActive = viewModel.sleepTimer.value != SleepTimerState.Off)
    }

    /** Follows the player about 4 times a second; it has no event for the position moving. */
    private fun startProgressUpdates() {
        progressUpdateJob?.cancel()
        progressUpdateJob = lifecycleScope.launch {
            while (true) {
                state = state.copy(
                    positionMs = viewModel.positionMs.coerceAtLeast(0L),
                    durationMs = viewModel.durationMs,
                    // The speed chosen, not the player's, which is faster while fast-forward is held
                    speed = viewModel.speed.value,
                    sleepTimerActive = viewModel.sleepTimer.value != SleepTimerState.Off,
                )
                delay(PROGRESS_UPDATE_INTERVAL_MS)
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
        startProgressUpdates()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        progressUpdateJob?.cancel()
        progressUpdateJob = null
        _binding = null
    }
}
