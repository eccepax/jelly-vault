package org.jellyfin.mobile.player.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Regression coverage for [formatTime], the player's position and length. */
class FormatTimeTest {

    @Test
    fun `zero is formatted as 0-00`() {
        formatTime(0L) shouldBe "0:00"
    }

    @Test
    fun `under a minute omits the hours segment`() {
        formatTime(45_000L) shouldBe "0:45"
    }

    @Test
    fun `exactly one hour includes the hours segment`() {
        formatTime(3_600_000L) shouldBe "1:00:00"
    }

    @Test
    fun `hours minutes and seconds all render together`() {
        formatTime(3_723_000L) shouldBe "1:02:03"
    }

    @Test
    fun `multi-hour durations are not truncated`() {
        formatTime(36_000_000L) shouldBe "10:00:00"
    }

    @Test
    fun `sub-second remainders are truncated, not rounded`() {
        formatTime(59_999L) shouldBe "0:59"
    }
}
