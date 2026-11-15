package org.jellyfin.mobile.common.playback

import kotlin.math.sqrt

/**
 * Tells a shake of the phone from its accelerometer: two jolts of [thresholdG] times gravity or
 * more within [JOLT_WINDOW_MS] of each other. Shaking on counts once: it counts again only after
 * the jolts have stopped for [SETTLE_MS]. Lying still, or one bump, isn't a shake.
 */
class ShakeDetector(private val thresholdG: Float = DEFAULT_THRESHOLD_G) {
    private var lastJoltAtMs: Long? = null
    private var counted = false

    /** One accelerometer sample (m/s², at [atMs]); true when it completes a shake. */
    fun onSample(x: Float, y: Float, z: Float, atMs: Long): Boolean {
        if (sqrt(x * x + y * y + z * z) / GRAVITY < thresholdG) return false
        val last = lastJoltAtMs
        lastJoltAtMs = atMs
        val sinceLast = last?.let { atMs - it }
        if (counted) {
            // Settled since the last jolt: this one starts a new shake
            if (sinceLast != null && sinceLast > SETTLE_MS) counted = false
            return false
        }
        counted = sinceLast != null && sinceLast <= JOLT_WINDOW_MS
        return counted
    }

    private companion object {
        const val GRAVITY = 9.81f
        const val DEFAULT_THRESHOLD_G = 2.5f
        const val JOLT_WINDOW_MS = 600L
        const val SETTLE_MS = 500L
    }
}
