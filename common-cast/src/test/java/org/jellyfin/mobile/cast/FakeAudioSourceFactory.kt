package org.jellyfin.mobile.cast

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.test.utils.ExoPlayerTestRunner
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.FakeTimeline

/**
 * Fake audio sources that each last 100 s and keep the requested [MediaItem] (so media ids survive).
 * With [placeholders], the player's timeline holds MediaItem.EMPTY for every item instead, the way
 * Media3's cast player's does for the items the cast device doesn't know.
 */
class FakeAudioSourceFactory(private val placeholders: Boolean = false) : MediaSource.Factory {
    override fun createMediaSource(mediaItem: MediaItem): MediaSource = FakeMediaSource(
        FakeTimeline(
            FakeTimeline.TimelineWindowDefinition.Builder()
                .setMediaItem(if (placeholders) MediaItem.EMPTY else mediaItem)
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
