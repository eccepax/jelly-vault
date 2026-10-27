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
        stopCasting = { events += "stop casting" },
        forgetDownloads = { events += "forget downloads" },
    )

    @Test
    fun `stops playback, then ends any cast, since the cast device holds stream urls with the token in them`() = runTest {
        signOut()

        events shouldBe listOf("stop playback", "stop casting", "end session", "forget downloads", "logout")
    }
}
