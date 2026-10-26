package org.jellyfin.mobile.cast

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.test.utils.ExoPlayerTestRunner
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.FakeTimeline

/** Fake audio sources that each last 100 s and keep the requested [MediaItem] (so media ids survive). */
class FakeAudioSourceFactory : MediaSource.Factory {
    override fun createMediaSource(mediaItem: MediaItem): MediaSource = FakeMediaSource(
        FakeTimeline(
            FakeTimeline.TimelineWindowDefinition.Builder()
                .setMediaItem(mediaItem)
                .setUid(mediaItem.mediaId)
                .setDurationUs(DURATION_US)
                .build(),
        ),
        ExoPlayerTestRunner.AUDIO_FORMAT,
    )

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory = this

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory = this

    override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_OTHER)

    private companion object {
        const val DURATION_US = 100_000_000L
    }
}
