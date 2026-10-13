import org.gradle.api.Project
import java.io.File
import java.util.*

object SigningHelper {

    fun loadSigningConfig(project: Project): Config? = loadFromEnv(project) ?: loadFromProperties(project)

    private fun loadFromEnv(project: Project): Config? {
        val serializedKeystore = System.getenv("KEYSTORE") ?: return null
        val storeFile = try {
            project.file("/tmp/keystore.jks").apply {
                writeBytes(Base64.getDecoder().decode(serializedKeystore))
            }
        } catch (e: RuntimeException) {
            return null
        }
        val storePassword = System.getenv("KEYSTORE_PASSWORD") ?: return null
        val keyAlias = System.getenv("KEY_ALIAS") ?: return null
        val keyPassword = System.getenv("KEY_PASSWORD") ?: return null

        return Config(storeFile, storePassword, keyAlias, keyPassword)
    }

    /**
     * Reads the app module's own keystore.properties (each app has its own key), falling back to one
     * at the repo root. A relative storeFile resolves against the directory holding the properties file.
     */
    private fun loadFromProperties(project: Project): Config? {
        val propsFile = listOf(project.file("keystore.properties"), project.rootProject.file("keystore.properties"))
            .firstOrNull(File::exists) ?: return null
        val props = Properties().apply { propsFile.inputStream().use(::load) }
        val storeFile = propsFile.parentFile.resolve(props.getProperty("storeFile") ?: return null)
        if (!storeFile.exists()) {
            project.logger.warn("Release signing skipped: keystore not found at $storeFile")
            return null
        }
        return Config(
            storeFile,
            props.getProperty("storePassword") ?: return null,
            props.getProperty("keyAlias") ?: return null,
            props.getProperty("keyPassword") ?: return null,
        )
    }

    data class Config(
        /**
         * Store file used when signing.
         */
        val storeFile: File,

        /**
         * Store password used when signing.
         */
        val storePassword: String,

        /**
         * Key alias used when signing.
         */
        val keyAlias: String,

        /**
         * Key password used when signing.
         */
        val keyPassword: String
    )
}
