package org.jellyfin.mobile.common.downloads

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * What to keep awake while a track plays with the screen off: a streamed one needs Wi-Fi too, one
 * playing from the device only the CPU (a Wi-Fi lock would just drain the battery).
 */
fun wakeModeFor(onDevice: Boolean): Int = if (onDevice) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NETWORK

/** Keeps the player's wake mode ([setWakeMode]) right for each track as it starts; see [wakeModeFor]. */
class WakeModeFollowsTrack(
    private val isOnDevice: (MediaItem) -> Boolean,
    private val setWakeMode: (Int) -> Unit,
) : Player.Listener {
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        setWakeMode(wakeModeFor(onDevice = mediaItem != null && isOnDevice(mediaItem)))
    }
}
