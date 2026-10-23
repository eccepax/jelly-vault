package org.jellyfin.mobile.common.ui

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ByteFormatTest {
    @Test
    fun `gigabytes get one decimal, dropped when whole`() {
        formatBytes(1_500_000_000L) shouldBe "1.5 GB"
        formatBytes(11_000_000_000L) shouldBe "11 GB"
    }

    @Test
    fun `megabytes and kilobytes are whole`() {
        formatBytes(32_400_000L) shouldBe "32 MB"
        formatBytes(4_096L) shouldBe "4 KB"
    }
}
