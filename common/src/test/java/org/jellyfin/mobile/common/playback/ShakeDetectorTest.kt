package org.jellyfin.mobile.common.playback

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** [ShakeDetector]: a couple of hard jolts close together is a shake; lying still, or one bump, isn't. */
class ShakeDetectorTest {

    private val detector = ShakeDetector()
    private val still = Triple(0f, 0f, GRAVITY)
    private val jolt = Triple(GRAVITY * 3, GRAVITY, GRAVITY)

    /** Feeds [samples] (each an acceleration and when, in ms), returning when a shake was seen. */
    private fun feed(vararg samples: Pair<Triple<Float, Float, Float>, Long>): List<Long> =
        samples.mapNotNull { (a, at) -> at.takeIf { detector.onSample(a.first, a.second, a.third, at) } }

    @Test
    fun `lying still is no shake`() {
        feed(*(0L..2_000L step 20).map { still to it }.toTypedArray()) shouldBe emptyList()
    }

    @Test
    fun `one bump is no shake`() {
        feed(still to 0L, jolt to 100L, still to 200L, still to 2_000L) shouldBe emptyList()
    }

    @Test
    fun `two jolts close together are a shake`() {
        feed(jolt to 0L, still to 150L, jolt to 300L) shouldBe listOf(300L)
    }

    @Test
    fun `two jolts far apart aren't`() {
        feed(jolt to 0L, still to 1_000L, jolt to 2_000L) shouldBe emptyList()
    }

    @Test
    fun `shaking on counts once until it has settled, not at every jolt`() {
        val shaking = (0L..1_400L step 200L).map {
            jolt to it
        } + (still to 1_600L) + (2_000L..2_400L step 200L).map { jolt to it }

        feed(*shaking.toTypedArray()) shouldBe listOf(200L, 2_200L)
    }

    private companion object {
        const val GRAVITY = 9.81f
    }
}
