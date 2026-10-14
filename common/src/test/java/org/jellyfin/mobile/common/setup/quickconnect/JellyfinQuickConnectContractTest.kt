package org.jellyfin.mobile.common.setup.quickconnect

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.common.server.JellyfinServerSession
import org.jellyfin.mobile.testing.VaultTestServer
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class JellyfinQuickConnectContractTest {

    @BeforeEach
    fun setUp() {
        VaultTestServer.assumeAvailable()
    }

    /** The device asking to sign in, like a TV that was just installed. */
    private val tv = VaultTestServer.newSignedOutClient("vault-quickconnect-test-${UUID.randomUUID()}")
    private val quickConnect = JellyfinQuickConnect(tv)

    @Test
    fun `the Vault has Quick Connect turned on`() = runBlocking<Unit> {
        quickConnect.isEnabled() shouldBe true
    }

    @Test
    fun `a new request has a six-digit code that isn't approved yet`() = runBlocking<Unit> {
        val request = quickConnect.request()

        request.code shouldMatch Regex("[0-9]{6}")
        quickConnect.isApproved(request.secret) shouldBe false
    }

    @Test
    fun `once approved from a signed-in device, the request signs in as that user`() = runBlocking<Unit> {
        val request = quickConnect.request()
        val approver = VaultTestServer.apiClient
        approver.quickConnectApi.authorizeQuickConnect(request.code)

        quickConnect.isApproved(request.secret) shouldBe true
        val user = quickConnect.signIn(request.secret)

        val signedIn = VaultTestServer.newSignedOutClient(
            tv.deviceInfo.id,
        ).apply { update(accessToken = user.accessToken) }
        try {
            signedIn.userApi.getCurrentUser().content.id.toString().replace("-", "") shouldBe
                user.userId.replace("-", "")
            signedIn.userApi.getCurrentUser().content.name shouldBe VaultTestServer.username
        } finally {
            JellyfinServerSession(signedIn).end()
        }
    }
}
