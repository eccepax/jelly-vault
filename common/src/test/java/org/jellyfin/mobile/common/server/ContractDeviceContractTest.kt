package org.jellyfin.mobile.common.server

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.testing.VaultTestServer
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.userApi
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Every test task (each module and variant) runs in its own JVM, in parallel with the others, and
 * each signs in to the Vault. These pin down why they must not share a device.
 */
class ContractDeviceContractTest {

    @BeforeEach
    fun setUp() {
        VaultTestServer.assumeAvailable()
    }

    @Test
    fun `signing in again as the same device ends the earlier sign-in`() = runBlocking<Unit> {
        val device = "vault-contract-device-test-${UUID.randomUUID()}"
        val earlier = VaultTestServer.newSignedInClient(device)
        VaultTestServer.newSignedInClient(device)

        val error = shouldThrow<InvalidStatusException> { earlier.userApi.getCurrentUser() }
        error.status shouldBe 401
    }

    @Test
    fun `the shared client signs in as a device of this test task alone`() {
        VaultTestServer.apiClient.deviceInfo.id shouldBe "vault-contract-tests-common-testDebugUnitTest"
    }
}
