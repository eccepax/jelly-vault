package org.jellyfin.mobile.robolectric

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.playback.SleepTimer
import org.jellyfin.mobile.player.AudiobookPlayerViewModel
import org.jellyfin.mobile.player.mediasegments.MediaSegmentAction
import org.jellyfin.mobile.player.playback.AudiobookQueueLoader
import org.jellyfin.mobile.player.playback.AudiobookSleepTimerTarget
import org.jellyfin.mobile.player.playback.FakeAudiobookController
import org.jellyfin.mobile.player.playback.MediaSegmentSkipper
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

/**
 * A real [AudiobookPlayerViewModel] over [controller], a fake of the playback service, for
 * Fragments under test: the screens see what a playing book looks like, and their buttons reach
 * [FakeAudiobookController.commands]. Books started through it are found on a server where every
 * item is called "Book".
 */
fun playerViewModel(
    controller: FakeAudiobookController = FakeAudiobookController(),
    speed: Float = 1f,
): AudiobookPlayerViewModel {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val loader = AudiobookQueueLoader(
        items = { ids -> ids.map { BaseItemDto(id = it, type = BaseItemKind.AUDIO_BOOK, name = "Book") } },
        playable = { null },
        coverUrl = { null },
    )
    val preferences = mockk<AudioVaultAppPreferences>(relaxed = true) { every { playbackSpeed } returns speed }
    return AudiobookPlayerViewModel(
        controller,
        loader,
        SleepTimer(AudiobookSleepTimerTarget(controller), scope),
        MediaSegmentSkipper(controller, { emptyList() }, { MediaSegmentAction.NOTHING }, scope),
        preferences,
    )
}
