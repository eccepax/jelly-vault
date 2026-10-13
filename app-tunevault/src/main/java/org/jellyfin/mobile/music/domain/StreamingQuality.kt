package org.jellyfin.mobile.music.domain

/** Effectively unlimited: even hi-res FLAC streams untouched. */
private const val UNLIMITED_BPS = 140_000_000
private const val HIGH_BPS = 320_000
private const val NORMAL_BPS = 192_000
private const val LOW_BPS = 128_000

/** How much data a stream may use. Below a song's own bitrate, the server transcodes it down. */
enum class StreamingQuality(val maxBitrate: Int) {
    ORIGINAL(UNLIMITED_BPS),
    HIGH(HIGH_BPS),
    NORMAL(NORMAL_BPS),
    LOW(LOW_BPS),
}
