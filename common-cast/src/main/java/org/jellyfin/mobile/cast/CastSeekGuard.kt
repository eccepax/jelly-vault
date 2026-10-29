package org.jellyfin.mobile.cast

import androidx.annotation.OptIn
import androidx.media3.common.DeviceInfo
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Drops seeks while playing on a cast device whose queue is empty for a moment ([castQueueEmpty]),
 * as it is between one load and the next: Media3's cast player looks the song up in that queue to
 * jump to it, and throws when it isn't there (next pressed over and over, say). The seek is lost;
 * the queue that comes back plays from where it was sent to.
 */
@OptIn(UnstableApi::class)
internal class CastSeekGuard(player: Player, private val castQueueEmpty: () -> Boolean) : ForwardingPlayer(player) {
    private val dropped: Boolean
        get() = deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE && castQueueEmpty()

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (!dropped) super.seekTo(mediaItemIndex, positionMs)
    }

    override fun seekTo(positionMs: Long) {
        if (!dropped) super.seekTo(positionMs)
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        if (!dropped) super.seekToDefaultPosition(mediaItemIndex)
    }

    override fun seekToDefaultPosition() {
        if (!dropped) super.seekToDefaultPosition()
    }

    override fun seekToNext() {
        if (!dropped) super.seekToNext()
    }

    override fun seekToNextMediaItem() {
        if (!dropped) super.seekToNextMediaItem()
    }

    override fun seekToPrevious() {
        if (!dropped) super.seekToPrevious()
    }

    override fun seekToPreviousMediaItem() {
        if (!dropped) super.seekToPreviousMediaItem()
    }

    override fun seekBack() {
        if (!dropped) super.seekBack()
    }

    override fun seekForward() {
        if (!dropped) super.seekForward()
    }
}
