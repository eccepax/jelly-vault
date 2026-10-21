package org.jellyfin.mobile.player.playback

import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.TsExtractor

private const val TS_SEARCH_PACKETS = 1800

/**
 * How the player reads the audio files it plays. A file with no seek table (a raw AAC) is sought in
 * by its bitrate, when its length is known: a transcode's isn't, so seeking one still restarts it.
 */
fun audioExtractorsFactory(isLowRamDevice: Boolean): DefaultExtractorsFactory = DefaultExtractorsFactory().apply {
    setConstantBitrateSeekingEnabled(true)
    // https://github.com/google/ExoPlayer/issues/8571
    setTsExtractorTimestampSearchBytes(
        when {
            !isLowRamDevice -> TS_SEARCH_PACKETS * TsExtractor.TS_PACKET_SIZE // 3x default
            else -> TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES
        },
    )
}
