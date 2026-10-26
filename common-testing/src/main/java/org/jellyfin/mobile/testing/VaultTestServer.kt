package org.jellyfin.mobile.testing

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.ServerVersion
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.net.HttpURLConnection
import java.net.URI

/**
 * A logged-in [ApiClient] for the test server, built straight from the SDK's JVM
 * [OkHttpFactory] (the `Jellyfin` entry point needs an Android Context; this doesn't).
 *
 * Configured through the `contract.*` system properties; a module's Gradle build sets them with
 * `useVaultContractServer` (buildSrc).
 */
object VaultTestServer {
    val hostname: String = System.getProperty("contract.hostname").orEmpty()
    val username: String = System.getProperty("contract.username").orEmpty()
    private val password = System.getProperty("contract.password").orEmpty()

    /** This test task's own device (see `useVaultContractServer`), so parallel tasks don't sign each other out. */
    private val deviceId = System.getProperty("contract.deviceId") ?: "vault-contract-tests"

    val apiClient: ApiClient by lazy { newSignedInClient() }

    /** The test server's Jellyfin version (the CI matrix runs several). */
    val version: ServerVersion by lazy {
        val info = runBlocking { apiClient.systemApi.getPublicSystemInfo().content }
        checkNotNull(info.version?.let(ServerVersion::fromString)) { "unreadable server version ${info.version}" }
    }

    /**
     * Whether the server counts a PDF's pages, 10,000 ticks each (Jellyfin 12 on). Before that every
     * book runs one second; BookVault scales a page to whatever the length is, so either works.
     */
    val countsPdfPages: Boolean get() = version >= PDF_PAGES_COUNTED_SINCE

    /**
     * A client signed in as its own device. The server keys sessions by device, so a test that ends
     * a session must use its own [deviceId], never [apiClient]'s, or it signs every other test out.
     */
    fun newSignedInClient(deviceId: String = this.deviceId): ApiClient =
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
    fun rawJson(path: String): JsonObject = rawJsonElement(path).jsonObject

    /** [rawJson] for endpoints that answer with a JSON array (e.g. "/Sessions"). */
    fun rawJsonArray(path: String): JsonArray = rawJsonElement(path).jsonArray

    private fun rawJsonElement(path: String): JsonElement {
        val connection = URI("$hostname$path").toURL().openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "MediaBrowser Token=\"${apiClient.accessToken}\"")
        return connection.inputStream.use { Json.parseToJsonElement(it.reader().readText()) }
    }

    /**
     * Skips when no credentials are configured (a plain build has no server). With credentials, an
     * unreachable server fails instead: a skip there would let a dead server pass as green.
     */
    fun assumeAvailable() {
        assumeTrue(
            username.isNotBlank() && password.isNotBlank(),
            "no test server credentials (VAULT_TEST_* env or local.properties)",
        )
        reachabilityError()?.let { fail<Unit>("Test server not reachable at $hostname (test-server/up.sh?): $it") }
    }

    private fun reachabilityError(): String? = runCatching {
        val connection = URI("$hostname/System/Info/Public").toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = REACHABLE_TIMEOUT_MS
        connection.readTimeout = REACHABLE_TIMEOUT_MS
        connection.responseCode.takeIf { it != HttpURLConnection.HTTP_OK }?.let { "HTTP $it" }
    }.getOrElse { it.toString() }
}

private const val REACHABLE_TIMEOUT_MS = 10_000
private val PDF_PAGES_COUNTED_SINCE = ServerVersion(major = 12, minor = 0, patch = 0)
