package org.jellyfin.mobile.player.source

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Basic functionality coverage for [effectiveStartTime], the resume-position fallback used when resolving a stream. */
class MediaSourceResolverTest {

    @Test
    fun `explicit start time wins over the server-stored position`() {
        effectiveStartTime(startTime = 30.seconds, playbackPositionTicks = 10_000_000L) shouldBe 30.seconds
    }

    @Test
    fun `falls back to a positive server-stored position when no explicit start time is given`() {
        effectiveStartTime(startTime = null, playbackPositionTicks = 50_000_000L) shouldBe 5.seconds
    }

    @Test
    fun `zero ticks is treated as no saved position, not a valid zero start time`() {
        effectiveStartTime(startTime = null, playbackPositionTicks = 0L) shouldBe null
    }

    @Test
    fun `negative ticks is treated as no saved position`() {
        effectiveStartTime(startTime = null, playbackPositionTicks = -10L) shouldBe null
    }

    @Test
    fun `missing server value with no explicit start time yields null`() {
        effectiveStartTime(startTime = null, playbackPositionTicks = null) shouldBe null
    }

    @Test
    fun `explicit zero duration is honored as-is, not treated as absent`() {
        effectiveStartTime(startTime = Duration.ZERO, playbackPositionTicks = 50_000_000L) shouldBe Duration.ZERO
    }
}
