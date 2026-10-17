package org.jellyfin.mobile.library

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Basic functionality coverage for [formatRuntime] — converts Jellyfin runtime ticks (100ns units) to a display string. */
class FormatRuntimeTest {

    private val ticksPerSecond = 10_000_000L

    @Test
    fun `zero ticks formats as seconds`() {
        formatRuntime(0L) shouldBe "0s"
    }

    @Test
    fun `under a minute formats as seconds`() {
        formatRuntime(45 * ticksPerSecond) shouldBe "45s"
    }

    @Test
    fun `exact one hour boundary formats as hours and zero minutes`() {
        formatRuntime(3600 * ticksPerSecond) shouldBe "1h 0m"
    }

    @Test
    fun `hours and minutes both present`() {
        formatRuntime((2 * 3600 + 15 * 60) * ticksPerSecond) shouldBe "2h 15m"
    }

    @Test
    fun `multi-day runtime formats as accumulated hours, not days`() {
        val thirtyHours = 30 * 3600 * ticksPerSecond
        formatRuntime(thirtyHours) shouldBe "30h 0m"
    }
}
