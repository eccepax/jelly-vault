package org.jellyfin.mobile.music.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class DurationFormatTest {

    @Test
    fun `under an hour shows minutes and zero-padded seconds`() {
        formatDuration(0L) shouldBe "0:00"
        formatDuration(5_000L) shouldBe "0:05"
        formatDuration(185_000L) shouldBe "3:05"
        formatDuration(3_599_000L) shouldBe "59:59"
    }

    @Test
    fun `an hour or more shows hours and zero-padded minutes`() {
        formatDuration(3_600_000L) shouldBe "1:00:00"
        formatDuration(3_723_000L) shouldBe "1:02:03"
    }

    @Test
    fun `partial seconds are truncated, not rounded up`() {
        formatDuration(59_999L) shouldBe "0:59"
    }

    @Test
    fun `negative durations show as zero`() {
        formatDuration(-1_000L) shouldBe "0:00"
    }
}
