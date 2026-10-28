package org.jellyfin.mobile.settings

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.common.server.ServerSession
import org.junit.jupiter.api.Test

/** AudioVault's own sign-out steps; the shared sequence is covered by common's SignOutTest. */
class AudioVaultSignOutTest {

    private val events = mutableListOf<String>()

    private val signOut = audioVaultSignOut(
        session = ServerSession { events += "end session" },
        logout = { events += "logout" },
        stopPlayback = { events += "stop playback" },
        forgetCast = { events += "forget cast" },
        forgetDownloads = { events += "forget downloads" },
    )

    @Test
    fun `ends any cast first, since the cast device holds stream urls with the token in them, then stops playback`() =
        runTest {
            // First, so a last report that fails can't leave it casting; it empties the cast's queue
            // itself, so it needn't wait on playback stopping
            signOut()

            events shouldBe listOf("forget cast", "stop playback", "end session", "forget downloads", "logout")
        }

    @Test
    fun `a cast is ended even if stopping playback fails`() = runTest {
        val failing = audioVaultSignOut(
            session = ServerSession { events += "end session" },
            logout = { events += "logout" },
            stopPlayback = { error("no route to host") },
            forgetCast = { events += "forget cast" },
            forgetDownloads = { events += "forget downloads" },
        )

        runCatching { failing() }

        events.first() shouldBe "forget cast"
    }
}
