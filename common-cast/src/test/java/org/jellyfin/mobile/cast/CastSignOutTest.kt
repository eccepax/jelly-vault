package org.jellyfin.mobile.cast

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Signing out while casting. */
class CastSignOutTest {

    private val events = mutableListOf<String>()

    @Test
    fun `playback stops first, then the cast is forgotten`() = runTest {
        stopPlaybackThenForgetCast(stopPlayback = { events += "stop" }, forgetCast = { events += "forget cast" })

        events shouldBe listOf("stop", "forget cast")
    }

    @Test
    fun `the cast is forgotten even if stopping fails`() = runTest {
        shouldThrow<IllegalStateException> {
            stopPlaybackThenForgetCast(stopPlayback = { error("no player") }, forgetCast = { events += "forget cast" })
        }

        events shouldBe listOf("forget cast")
    }
}
