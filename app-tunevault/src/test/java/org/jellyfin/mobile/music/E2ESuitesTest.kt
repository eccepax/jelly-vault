package org.jellyfin.mobile.music

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Guards the e2e suite tags (androidTest e2e/E2ESuites.kt): a class with no area would silently
 * drop out of every area run, and two areas would run it twice. Reads the sources, since the
 * instrumented classes aren't on the unit-test classpath.
 */
class E2ESuitesTest {

    private val e2eDir = File("src/androidTest/java/org/jellyfin/mobile/music/e2e")
    private val areas = listOf("BrowseArea", "PlaybackArea", "CollectionsArea", "AccountArea", "DownloadsArea")
    private val testClasses = e2eDir.listFiles { file -> file.name.endsWith("E2ETest.kt") }.orEmpty()
        .map { it to it.readText() }
        .filter { (_, source) -> Regex("""^class \w+E2ETest""", RegexOption.MULTILINE).containsMatchIn(source) }

    @Test
    fun `every e2e test class is in exactly one area`() {
        testClasses.shouldNotBeEmpty()

        val misfiled = testClasses.mapNotNull { (file, source) ->
            val tagged = areas.filter { Regex("""^@$it\s*$""", RegexOption.MULTILINE).containsMatchIn(source) }
            file.name.takeIf { tagged.size != 1 }?.let { "$it: $tagged" }
        }

        misfiled.shouldBeEmpty()
    }

    @Test
    fun `there is a smoke suite`() {
        testClasses.filter { (_, source) -> source.contains("@Smoke") }.shouldNotBeEmpty()
    }
}
