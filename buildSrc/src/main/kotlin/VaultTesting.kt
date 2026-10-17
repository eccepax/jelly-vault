import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import java.util.Properties

/** The gitignored local.properties (e2e credentials, sdk.dir); empty when there isn't one. */
fun Project.vaultLocalProperties(): Properties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/** [VaultTestSettings] for this build, logging (once per build, not per module) why it dropped any credentials. */
fun Project.vaultTestSettings(): VaultTestSettings = VaultTestSettings(vaultLocalProperties()).also { settings ->
    val extra = rootProject.extensions.extraProperties
    if (settings.warnings.isNotEmpty() && !extra.has(WARNED)) {
        extra.set(WARNED, true)
        settings.warnings.forEach { logger.warn("w: $it") }
    }
}

private const val WARNED = "vaultTestSettingsWarned"

/**
 * Where the server-backed tests sign in: by default the disposable test server (test-server/up.sh),
 * on port 8097 so it runs beside a real Jellyfin on 8096.
 *
 * The settings come from the VAULT_TEST_* environment variables when any is set, else from
 * local.properties. Never a mix of the two: credentials for one server must never be sent to the
 * address of another. The environment wins so CI, or a single run, can point at a server without
 * anyone editing local.properties. Blank credentials make the tests skip.
 *
 * For the same reason the default address only goes with the test server's own credentials. Other
 * credentials with no address beside them were meant for some other server, so they are dropped
 * (and the tests skip) rather than sent to the default.
 */
class VaultTestSettings(private val localProperties: Properties) {
    /** An address and the credentials to send to it. */
    data class Server(val hostname: String, val username: String, val password: String)

    private val fromEnvironment = ENVIRONMENT.any { !System.getenv(it).isNullOrBlank() }

    private fun setting(env: String, property: String): String? =
        if (fromEnvironment) System.getenv(env)?.takeIf { it.isNotBlank() }
        else localProperties.getProperty(property)?.takeIf { it.isNotBlank() }

    private val username = setting("VAULT_TEST_USERNAME", "e2e.username").orEmpty()
    private val password = setting("VAULT_TEST_PASSWORD", "e2e.password").orEmpty()

    private val dropped = mutableListOf<String>()

    /** Why credentials were dropped, if they were. */
    val warnings: List<String> get() = dropped

    private fun server(env: String, property: String, default: String): Server {
        setting(env, property)?.let { return Server(it, username, password) }
        if (username.isEmpty() || (username == TEST_SERVER_USERNAME && password == TEST_SERVER_PASSWORD)) {
            return Server(default, username, password)
        }
        val missing = if (fromEnvironment) env else "$property in local.properties"
        dropped += "test credentials are set but $missing isn't, and they aren't the test server's, so they " +
            "won't be sent to the default $default. The server tests will skip; set $missing."
        return Server(default, "", "")
    }

    private companion object {
        val ENVIRONMENT = listOf("VAULT_TEST_HOSTNAME", "VAULT_TEST_E2E_HOSTNAME", "VAULT_TEST_USERNAME", "VAULT_TEST_PASSWORD")

        // The user in test-server/fixtures.json.
        const val TEST_SERVER_USERNAME = "vault-test"
        const val TEST_SERVER_PASSWORD = "vault-test"
    }

    /** As the JVM contract tests reach the server. */
    val contract = server("VAULT_TEST_HOSTNAME", "contract.hostname", "http://localhost:8097")

    /** As the emulator reaches it: 10.0.2.2 is the emulator's alias for the host machine. */
    val emulator = server("VAULT_TEST_E2E_HOSTNAME", "e2e.hostname", "http://10.0.2.2:8097")
}

/**
 * Points contract tests (common-testing's VaultTestServer) at the test server. They run on the JVM,
 * so the server is [VaultTestSettings.contract], not the emulator's alias. They are skipped when
 * the credentials are missing, and fail when the server is unreachable.
 *
 * Each test task signs in as its own device: test tasks run in parallel, and signing in again as a
 * device ends that device's earlier sign-in, so a shared device made them sign each other out.
 */
fun Test.useVaultContractServer(settings: VaultTestSettings) {
    systemProperty("contract.hostname", settings.contract.hostname)
    systemProperty("contract.username", settings.contract.username)
    systemProperty("contract.password", settings.contract.password)
    systemProperty("contract.deviceId", "vault-contract-tests" + path.replace(':', '-'))
}
