package org.jellyfin.mobile.robolectric

import android.os.Looper
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.TrackSelectionHelper
import org.jellyfin.mobile.player.queue.QueueManager

/**
 * A relaxed-mocked [AudiobookPlayerViewModel] safe for a Fragment under test to observe: its
 * [AudiobookPlayerViewModel.player] and [QueueManager.currentMediaSource] LiveData never emit, so
 * the mini-player wiring in [org.jellyfin.mobile.library.LibraryFragment.onCreateView] doesn't
 * need a real ExoPlayer/JellyfinMediaSource to attach observers to.
 */
fun mockedPlayerViewModel(): AudiobookPlayerViewModel {
    val queueManager = mockk<QueueManager>(relaxed = true)
    every { queueManager.currentMediaSource } returns MutableLiveData()

    val playerViewModel = mockk<AudiobookPlayerViewModel>(relaxed = true)
    every { playerViewModel.player } returns MutableLiveData(null)
    every { playerViewModel.queueManager } returns queueManager
    return playerViewModel
}

/**
 * A relaxed-mocked [AudiobookPlayerViewModel] for testing [org.jellyfin.mobile.player.ui.PlayerFragment]
 * directly — distinct from [mockedPlayerViewModel] above (LibraryFragment's minimal mini-player needs).
 *
 * Stubs a non-null mocked [ExoPlayer] rather than `null`: this Fragment's entire reason to exist is
 * reconnecting the UI to an *already-playing* session (see the `by activityViewModel()` comment in
 * PlayerFragment.kt), and `viewModel.player` emitting `null` triggers an immediate
 * `parentFragmentManager.popBackStack()` in `onCreate`, which isn't the scenario under test.
 * [ExoPlayer] is a Media3 interface, so mocking it is a plain, safe interface proxy — no real
 * ExoPlayer/MediaSession construction happens anywhere in this test.
 */
fun mockedPlayerViewModelForPlayerFragment(playWhenReady: Boolean = false): AudiobookPlayerViewModel {
    val mockedPlayer = mockk<ExoPlayer>(relaxed = true)
    every { mockedPlayer.playWhenReady } returns playWhenReady
    // PlayerView.setPlayer() asserts the player's applicationLooper matches the main looper.
    every { mockedPlayer.applicationLooper } returns Looper.getMainLooper()

    val queueManager = mockk<QueueManager>(relaxed = true)
    every { queueManager.currentMediaSource } returns MutableLiveData() // never emits: no album-art load
    every { queueManager.hasNext() } returns false
    every { queueManager.hasPrevious() } returns false
    every { queueManager.queueSize } returns 1
    every { queueManager.currentIndex } returns 0
    every { queueManager.getTrackNames() } returns emptyList()

    val trackSelectionHelper = mockk<TrackSelectionHelper>(relaxed = true)

    val playerViewModel = mockk<AudiobookPlayerViewModel>(relaxed = true)
    every { playerViewModel.player } returns MutableLiveData(mockedPlayer)
    every { playerViewModel.playerOrNull } returns mockedPlayer
    every { playerViewModel.playerState } returns MutableLiveData(Player.STATE_READY)
    every { playerViewModel.skipSegment } returns MutableStateFlow(null)
    every { playerViewModel.error } returns MutableLiveData()
    every { playerViewModel.queueManager } returns queueManager
    every { playerViewModel.trackSelectionHelper } returns trackSelectionHelper
    every { playerViewModel.adjustedCurrentPosition } returns 0L
    every { playerViewModel.adjustedBufferedPosition } returns 0L
    return playerViewModel
}
