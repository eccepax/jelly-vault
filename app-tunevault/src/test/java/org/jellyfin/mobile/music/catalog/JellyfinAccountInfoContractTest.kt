package org.jellyfin.mobile.music.catalog

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.testing.VaultTestServer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JellyfinAccountInfoContractTest {

    private lateinit var account: AccountInfo

    @BeforeEach
    fun setUp() {
        VaultTestServer.assumeAvailable()
        account = JellyfinAccountInfo(VaultTestServer.apiClient)
    }

    @Test
    fun `names the signed-in user and the server`() = runBlocking<Unit> {
        val current = account.current()

        current.userName shouldBe VaultTestServer.username
        current.serverUrl shouldBe VaultTestServer.hostname
        current.serverName.shouldNotBeBlank()
    }
}
