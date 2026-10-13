package org.jellyfin.mobile.music.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.test.utils.ExoPlayerTestRunner
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.FakeTimeline

/**
 * Fake audio sources that each last 10 s and keep the requested [MediaItem] (so media ids survive).
 * Media3's own FakeMediaSourceFactory makes 1000 s items, too long to play through in a test.
 */
class TenSecondMediaSourceFactory : MediaSource.Factory {
    override fun createMediaSource(mediaItem: MediaItem): MediaSource = FakeMediaSource(
        FakeTimeline(
            FakeTimeline.TimelineWindowDefinition.Builder()
                .setMediaItem(mediaItem)
                .setUid(mediaItem.mediaId)
                .setDurationUs(TRACK_DURATION_US)
                .build(),
        ),
        ExoPlayerTestRunner.AUDIO_FORMAT,
    )

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory = this

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory = this

    override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_OTHER)

    private companion object {
        const val TRACK_DURATION_US = 10_000_000L
    }
}
