package org.jellyfin.mobile.player.playback

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.test.utils.ExoPlayerTestRunner
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.FakeTimeline
import java.io.IOException

/**
 * Fake audio sources of [durationMs] that keep the requested [MediaItem] (so media ids and
 * metadata survive). Like TuneVault's TenSecondMediaSourceFactory, but long enough for chapters.
 * [isSeekable] false stands for a live transcode, which the player can't seek in. The tracks in
 * [failing] can't be loaded, as when the server can't be reached; one taken out of it loads on the
 * next try.
 */
class FakeAudioSourceFactory(
    private val durationMs: Long = 100_000L,
    private val isSeekable: Boolean = true,
    private val failing: Set<String> = emptySet(),
) : MediaSource.Factory {
    /** Every item a source was made for, in order: shows when a stream was reloaded. */
    val created = mutableListOf<MediaItem>()

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        created += mediaItem
        if (mediaItem.mediaId in failing) return Unreachable { mediaItem.mediaId in failing }
        return FakeMediaSource(
            FakeTimeline(
                FakeTimeline.TimelineWindowDefinition.Builder()
                    .setMediaItem(mediaItem)
                    .setUid(mediaItem.mediaId + created.size)
                    .setDurationUs(durationMs * US_PER_MS)
                    .setSeekable(isSeekable)
                    .build(),
            ),
            ExoPlayerTestRunner.AUDIO_FORMAT,
        )
    }

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory = this

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory = this

    override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_OTHER)

    /** Doesn't load while [isFailing]: the player gives up with a source error. */
    private class Unreachable(private val isFailing: () -> Boolean) : FakeMediaSource() {
        init {
            setAllowPreparation(false)
        }

        override fun maybeThrowSourceInfoRefreshError() {
            if (isFailing()) throw IOException("No route to host")
            setAllowPreparation(true)
        }
    }

    private companion object {
        const val US_PER_MS = 1_000L
    }
}
