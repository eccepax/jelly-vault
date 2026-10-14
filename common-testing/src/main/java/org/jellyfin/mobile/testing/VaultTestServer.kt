package org.jellyfin.mobile.testing

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.HttpURLConnection
import java.net.URI

/**
 * A logged-in [ApiClient] for the real local Vault, built straight from the SDK's JVM
 * [OkHttpFactory] (the `Jellyfin` entry point needs an Android Context; this doesn't).
 *
 * Configured through the `contract.*` system properties; a module's Gradle build sets them with
 * `useVaultContractServer` (buildSrc).
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
    fun newSignedInClient(deviceId: String = "vault-contract-tests"): ApiClient =
        newSignedOutClient(deviceId).apply {
            val auth = runBlocking { userApi.authenticateUserByName(username, password).content }
            update(accessToken = auth.accessToken)
        }

    /** A client that hasn't signed in, as a fresh install would be (for sign-in flows). */
    fun newSignedOutClient(deviceId: String): ApiClient {
        val factory = OkHttpFactory()
        return factory.create(
            baseUrl = hostname,
            accessToken = null,
            clientInfo = ClientInfo("Vault contract tests", "test"),
            deviceInfo = DeviceInfo(deviceId, "JVM"),
            httpClientOptions = HttpClientOptions(),
            socketConnectionFactory = factory,
        )
    }

    /**
     * The server's raw JSON answer to an authenticated GET of [path] (e.g. "/Items?ids=..."), for
     * checking what the server really stores without going through the SDK's decoding.
     */
    fun rawJson(path: String): JsonObject {
        val connection = URI("$hostname$path").toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "MediaBrowser Token=\"${apiClient.accessToken}\"")
        return connection.inputStream.use { Json.parseToJsonElement(it.reader().readText()).jsonObject }
    }

    /**
     * Skips when no credentials are configured (CI has no server). With credentials, an unreachable
     * Vault fails instead: a skip there would let a dead server pass as green.
     */
    fun assumeAvailable() {
        assumeTrue(username.isNotBlank() && password.isNotBlank(), "e2e credentials not in local.properties")
        reachabilityError()?.let { fail<Unit>("Vault not reachable at $hostname: $it") }
    }

    private fun reachabilityError(): String? = runCatching {
        val connection = URI("$hostname/System/Info/Public").toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = REACHABLE_TIMEOUT_MS
        connection.readTimeout = REACHABLE_TIMEOUT_MS
        connection.responseCode.takeIf { it != HttpURLConnection.HTTP_OK }?.let { "HTTP $it" }
    }.getOrElse { it.toString() }
}

private const val REACHABLE_TIMEOUT_MS = 10_000
