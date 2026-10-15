import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import java.util.Properties

/** The gitignored local.properties (e2e credentials, sdk.dir); empty when there isn't one. */
fun Project.vaultLocalProperties(): Properties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/**
 * Points contract tests (common-testing's VaultTestServer) at the real Vault. They run on the JVM,
 * so the host is localhost, not the emulator's 10.0.2.2 alias that e2e.hostname defaults to. They
 * are skipped when the credentials are missing, and fail when the Vault is unreachable.
 *
 * Each test task signs in as its own device: test tasks run in parallel, and signing in again as a
 * device ends that device's earlier sign-in, so a shared device made them sign each other out.
 */
fun Test.useVaultContractServer(localProperties: Properties) {
    systemProperty("contract.hostname", localProperties.getProperty("contract.hostname", "http://localhost:8096"))
    systemProperty("contract.username", localProperties.getProperty("e2e.username", ""))
    systemProperty("contract.password", localProperties.getProperty("e2e.password", ""))
    systemProperty("contract.deviceId", "vault-contract-tests" + path.replace(':', '-'))
}
