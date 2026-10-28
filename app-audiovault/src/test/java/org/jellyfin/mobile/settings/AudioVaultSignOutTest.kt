package org.jellyfin.mobile.settings

import io.kotest.matchers.collections.shouldContain
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
    fun `stops playback, sending where the listener got to, then ends any cast, since it holds urls with the token`() =
        runTest {
            // The listen ends first: forgetting the cast empties the queue, and with it the place
            signOut()

            events shouldBe listOf("stop playback", "forget cast", "end session", "forget downloads", "logout")
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

        events shouldContain "forget cast"
    }
}
