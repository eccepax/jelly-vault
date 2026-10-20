package org.jellyfin.mobile.player.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jellyfin.mobile.player.mediasegments.MediaSegmentAction
import org.jellyfin.sdk.model.api.MediaSegmentDto

/**
 * Segments the server marks in the playing track (an intro, a recap, an outro): those set to skip
 * are skipped as playback reaches them, and those set to ask are [offered] while they play. App-wide,
 * so skipping happens with the app's screens gone too. Must run on the same thread as [controller].
 */
class MediaSegmentSkipper(
    private val controller: AudiobookPlaybackController,
    private val segmentsOf: suspend (itemId: String) -> List<MediaSegmentDto>,
    private val actionOf: (MediaSegmentDto) -> MediaSegmentAction,
    private val scope: CoroutineScope,
) {
    private val _offered = MutableStateFlow<MediaSegmentDto?>(null)

    /** The segment playing that the listener is offered to skip; null when there's none. */
    val offered: StateFlow<MediaSegmentDto?> = _offered.asStateFlow()

    fun start(): Job = scope.launch {
        controller.state
            .map { it.currentTrack?.id }
            .distinctUntilChanged()
            .collectLatest { itemId ->
                _offered.value = null
                if (itemId == null) return@collectLatest
                val segments = segmentsOf(itemId).groupBy(actionOf)
                watch(segments[MediaSegmentAction.SKIP].orEmpty(), segments[MediaSegmentAction.ASK_TO_SKIP].orEmpty())
            }
    }

    /** Jumps just past the offered segment. */
    fun skipOffered() {
        val segment = _offered.value ?: return
        _offered.value = null
        controller.seekTo(segment.endMs + 1)
    }

    private suspend fun watch(toSkip: List<MediaSegmentDto>, toOffer: List<MediaSegmentDto>) {
        if (toSkip.isEmpty() && toOffer.isEmpty()) return
        while (true) {
            val positionMs = controller.positionMs
            val skipping = toSkip.firstOrNull { positionMs in it }
            if (skipping != null) controller.seekTo(skipping.endMs)
            _offered.value = toOffer.firstOrNull { positionMs in it }
            delay(TICK_MS)
        }
    }

    private val MediaSegmentDto.startMs: Long get() = startTicks / TICKS_PER_MS
    private val MediaSegmentDto.endMs: Long get() = endTicks / TICKS_PER_MS

    private operator fun MediaSegmentDto.contains(positionMs: Long) = positionMs >= startMs && positionMs < endMs

    companion object {
        /** How often playback is checked against the segments. */
        const val TICK_MS = 500L
        private const val TICKS_PER_MS = 10_000L
    }
}
