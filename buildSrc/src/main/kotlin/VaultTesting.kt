import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import java.util.Properties

/** The gitignored local.properties (e2e credentials, sdk.dir); empty when there isn't one. */
fun Project.vaultLocalProperties(): Properties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/**
 * Where the server-backed tests sign in: by default the disposable test server (test-server/up.sh),
 * on port 8097 so it runs beside a real Jellyfin on 8096.
 *
 * The settings come from the VAULT_TEST_* environment variables when any is set, else from
 * local.properties, each falling back to its default. Never a mix of the two: credentials for one
 * server must never be sent to the address of another. The environment wins so CI, or a single
 * run, can point at a server without anyone editing local.properties. Blank credentials make the
 * tests skip.
 */
class VaultTestSettings(private val localProperties: Properties) {
    private val fromEnvironment = ENVIRONMENT.any { !System.getenv(it).isNullOrBlank() }

    private fun setting(env: String, property: String, default: String): String =
        if (fromEnvironment) System.getenv(env)?.takeIf { it.isNotBlank() } ?: default
        else localProperties.getProperty(property, default)

    private companion object {
        val ENVIRONMENT = listOf("VAULT_TEST_HOSTNAME", "VAULT_TEST_E2E_HOSTNAME", "VAULT_TEST_USERNAME", "VAULT_TEST_PASSWORD")
    }

    /** As the JVM contract tests reach the server. */
    val hostname = setting("VAULT_TEST_HOSTNAME", "contract.hostname", "http://localhost:8097")

    /** As the emulator reaches it: 10.0.2.2 is the emulator's alias for the host machine. */
    val emulatorHostname = setting("VAULT_TEST_E2E_HOSTNAME", "e2e.hostname", "http://10.0.2.2:8097")
    val username = setting("VAULT_TEST_USERNAME", "e2e.username", "")
    val password = setting("VAULT_TEST_PASSWORD", "e2e.password", "")
}

/**
 * Points contract tests (common-testing's VaultTestServer) at the test server. They run on the JVM,
 * so the host is [VaultTestSettings.hostname], not the emulator's alias. They are skipped when the
 * credentials are missing, and fail when the server is unreachable.
 *
 * Each test task signs in as its own device: test tasks run in parallel, and signing in again as a
 * device ends that device's earlier sign-in, so a shared device made them sign each other out.
 */
fun Test.useVaultContractServer(settings: VaultTestSettings) {
    systemProperty("contract.hostname", settings.hostname)
    systemProperty("contract.username", settings.username)
    systemProperty("contract.password", settings.password)
    systemProperty("contract.deviceId", "vault-contract-tests" + path.replace(':', '-'))
}
