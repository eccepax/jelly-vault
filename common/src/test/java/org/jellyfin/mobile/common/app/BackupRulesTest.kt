package org.jellyfin.mobile.common.app

import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Backups and moves to a new phone leave out the sign-in: each app's server token lives in its
 * database, so no copy of it leaves the device. Settings still come along; the server, sign-in and
 * downloads start over (downloads would be far too big for a backup anyway).
 */
class BackupRulesTest {

    private val xml = File("src/main/res/xml")

    private fun rules(file: String, section: String? = null): List<Pair<String, String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(xml, file))
        val root = section?.let { doc.getElementsByTagName(it).item(0) as Element } ?: doc.documentElement
        val excludes = root.getElementsByTagName("exclude")
        return (0 until excludes.length).map { i ->
            val exclude = excludes.item(i) as Element
            exclude.getAttribute("domain") to exclude.getAttribute("path")
        }
    }

    private val leftOut = listOf("database" to ".", "file" to "downloaded")

    @Test
    fun `backups on Android 11 and older leave out the databases and downloads`() {
        rules("backup_rules.xml") shouldContainAll leftOut
    }

    @Test
    fun `cloud backups and phone-to-phone moves on Android 12 and newer do too`() {
        rules("data_extraction_rules.xml", "cloud-backup") shouldContainAll leftOut
        rules("data_extraction_rules.xml", "device-transfer") shouldContainAll leftOut
    }

    @Test
    fun `settings still come along`() {
        val sharedPrefs = "sharedpref" to "."
        rules("backup_rules.xml") shouldNotContain sharedPrefs
        rules("data_extraction_rules.xml", "cloud-backup") shouldNotContain sharedPrefs
        rules("data_extraction_rules.xml", "device-transfer") shouldNotContain sharedPrefs
    }

    @Test
    fun `every app backs up by these rules`() {
        listOf("app-audiovault", "app-bookvault", "app-tunevault", "app-livevault").forEach { app ->
            val manifest = File("../$app/src/main/AndroidManifest.xml").readText()
            withClue(app) {
                manifest shouldContainAttribute "android:fullBackupContent=\"@xml/backup_rules\""
                manifest shouldContainAttribute "android:dataExtractionRules=\"@xml/data_extraction_rules\""
            }
        }
    }

    private infix fun String.shouldContainAttribute(attribute: String) =
        (attribute in this) shouldBe true

    @Test
    fun `the old AudioVault rules, which included the database, are gone`() {
        File("../app-audiovault/src/main/res/xml").list().orEmpty().toList() shouldNotContain "backup_content.xml"
        xml.list().orEmpty().toList() shouldContain "backup_rules.xml"
    }
}
