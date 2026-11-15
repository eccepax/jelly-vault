package org.jellyfin.mobile.settings

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.common.server.JellyfinServerSession
import org.jellyfin.mobile.player.playback.JellyfinAudiobooks
import org.jellyfin.mobile.testing.VaultFixtures.Audiobooks
import org.jellyfin.mobile.testing.VaultTestServer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URI

/**
 * Signing out against the test server: a stream address carries the access token (a cast device
 * fetches it with nothing else), so ending the session on the server must stop that address working.
 * Signs in as a device of its own, so the other tests' session stays.
 */
class SignOutContractTest {

    @BeforeEach
    fun setUp() = VaultTestServer.assumeAvailable()

    @Test
    fun `signing out revokes the token a cast stream address carries`() = runBlocking<Unit> {
        val signedIn = VaultTestServer.newSignedInClient(deviceId = "vault-contract-sign-out")
        val audiobooks = JellyfinAudiobooks(signedIn)
        val book = checkNotNull(audiobooks.topMatch(Audiobooks.M4B_SEARCH_TERM)) { "no m4b on the test server" }
        // What a cast device is sent: the address alone, the token in it
        val streamUrl = audiobooks.castStream(book.toString()).url
        status(streamUrl) shouldBe HttpURLConnection.HTTP_OK

        JellyfinServerSession(signedIn).end()

        status(streamUrl) shouldBe HttpURLConnection.HTTP_UNAUTHORIZED
    }

    private fun status(url: String): Int {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        return try {
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }
}
