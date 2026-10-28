package org.jellyfin.mobile.cast

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/** Work waiting for the Cast framework to start. */
class StartGateTest {

    private val gate = StartGate()
    private val ran = mutableListOf<String>()

    @Test
    fun `what waits runs once started, and what comes after runs at once`() {
        gate.whenReady { ran += "first" }
        ran shouldBe emptyList()

        gate.started()
        gate.whenReady { ran += "second" }

        ran shouldBe listOf("first", "second")
    }

    @Test
    fun `if it never starts, nothing is kept waiting for it`() {
        // Each holds a playback service's session and player: kept, they'd stay in memory for good
        gate.whenReady { ran += "first" }

        gate.failed()
        gate.whenReady { ran += "second" }

        gate.waiting shouldBe 0
        ran shouldBe emptyList()
    }

    @Test
    fun `what's taken back before it starts never runs, and isn't kept`() {
        val cancel = gate.whenReady { ran += "gone" }

        cancel()
        gate.waiting shouldBe 0
        gate.started()

        ran shouldBe emptyList()
    }
}
