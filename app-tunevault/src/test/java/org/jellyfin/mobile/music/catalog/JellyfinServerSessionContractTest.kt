package org.jellyfin.mobile.music.catalog

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.common.server.JellyfinServerSession
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.userApi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class JellyfinServerSessionContractTest {

    @BeforeEach
    fun setUp() {
        VaultTestServer.assumeAvailable()
    }

    /** A separate device, as another app or install would be; ended by each test. */
    private fun newDevice() = VaultTestServer.newSignedInClient("tunevault-session-test-${UUID.randomUUID()}")

    @Test
    fun `ending the session revokes its token on the server`() = runBlocking<Unit> {
        val client = newDevice()
        client.userApi.getCurrentUser().status shouldBe 200

        JellyfinServerSession(client).end()

        shouldThrow<InvalidStatusException> { client.userApi.getCurrentUser() }.status shouldBe 401
    }

    @Test
    fun `ending one session leaves other sign-ins working`() = runBlocking<Unit> {
        val other = newDevice()

        JellyfinServerSession(newDevice()).end()

        try {
            other.userApi.getCurrentUser().status shouldBe 200
        } finally {
            JellyfinServerSession(other).end()
        }
    }
}
