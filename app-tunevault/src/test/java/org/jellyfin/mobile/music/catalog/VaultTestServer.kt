package org.jellyfin.mobile.music.catalog

import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.HttpURLConnection
import java.net.URI

/**
 * A logged-in [ApiClient] for the real local Vault, built straight from the SDK's JVM
 * [OkHttpFactory] (the `Jellyfin` entry point needs an Android Context; this doesn't). Tests using
 * it are skipped, not failed, when the Vault is down or no credentials are configured.
 */
object VaultTestServer {
    val hostname: String = System.getProperty("contract.hostname").orEmpty()
    val username: String = System.getProperty("contract.username").orEmpty()
    private val password = System.getProperty("contract.password").orEmpty()

    val apiClient: ApiClient by lazy { newSignedInClient() }

    /**
     * A client signed in as its own device. The server keys sessions by device, so a test that ends
     * a session must use its own [deviceId], never [apiClient]'s, or it signs every other test out.
     */
    fun newSignedInClient(deviceId: String = "tunevault-contract-tests"): ApiClient {
        val factory = OkHttpFactory()
        return factory.create(
            baseUrl = hostname,
            accessToken = null,
            clientInfo = ClientInfo("TuneVault contract tests", "test"),
            deviceInfo = DeviceInfo(deviceId, "JVM"),
            httpClientOptions = HttpClientOptions(),
            socketConnectionFactory = factory,
        ).apply {
            val auth = runBlocking { userApi.authenticateUserByName(username, password).content }
            update(accessToken = auth.accessToken)
        }
    }

    fun assumeAvailable() {
        assumeTrue(username.isNotBlank() && password.isNotBlank(), "e2e credentials not in local.properties")
        assumeTrue(isReachable(), "Vault not reachable at $hostname")
    }

    private fun isReachable(): Boolean = runCatching {
        val connection = URI("$hostname/System/Info/Public").toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        connection.responseCode == 200
    }.getOrDefault(false)
}
