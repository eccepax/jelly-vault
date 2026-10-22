package org.jellyfin.mobile.testsupport

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class E2ESuiteTagsTest {

    @TempDir
    lateinit var e2eDir: File

    private val tags by lazy { E2ESuiteTags(e2eDir, listOf("BrowseArea", "PlaybackArea")) }

    private fun source(name: String, code: String) = File(e2eDir, name).writeText(code)

    @Test
    fun `a class in one area, with a smoke test, is fine`() {
        source(
            "SearchE2ETest.kt",
            """
            @BrowseArea
            class SearchE2ETest : BaseE2ETest() {
                @Smoke
                @Test
                fun searching() {}

                @Test
                fun `clearing the search`() {}
            }
            """.trimIndent(),
        )

        tags.problems().shouldBeEmpty()
        tags.smokeTests().shouldContainExactly("SearchE2ETest.searching")
    }

    @Test
    fun `a class in no area, or in two, is a problem`() {
        source("NoAreaE2ETest.kt", "class NoAreaE2ETest {\n @Test fun a() {}\n}")
        source("TwoAreasE2ETest.kt", "@BrowseArea @PlaybackArea\nclass TwoAreasE2ETest {\n @Test fun a() {}\n}")

        tags.problems() shouldHaveSize 2
    }

    @Test
    fun `smoke on a whole class is a problem`() {
        source("PlaybackE2ETest.kt", "@Smoke\n@PlaybackArea\nclass PlaybackE2ETest {\n @Test fun a() {}\n}")

        tags.problems().single() shouldContain "@Smoke on the class"
    }

    @Test
    fun `tests are found whatever their file or class is called and however they're declared`() {
        source("Helpers.kt", "internal class Misfiled {\n  @Test\n  fun a() {}\n}")
        File(e2eDir, "nested").mkdir()
        source("nested/Deep.kt", "public open class Deep {\n @org.junit.Test fun a() {}\n}")

        tags.problems() shouldHaveSize 2
    }

    @Test
    fun `commented-out, quoted and look-alike tags don't count`() {
        source(
            "LookAlikeE2ETest.kt",
            """
            // @PlaybackArea
            /* @Smoke */
            @BrowseArea
            class LookAlikeE2ETest {
                @SmokeTestHelper
                @Test
                fun a() { println("@Smoke") }
            }
            """.trimIndent(),
        )

        tags.problems().shouldBeEmpty()
        tags.smokeTests().shouldBeEmpty()
    }

    @Test
    fun `base classes, helpers and the tags themselves aren't test classes`() {
        source("BaseE2ETest.kt", "abstract class BaseE2ETest {\n @Test fun shared() {}\n}")
        source("E2ESuites.kt", "@Target(AnnotationTarget.CLASS)\nannotation class BrowseArea")
        source("Fixtures.kt", "class Fixtures {\n fun album() = Unit\n}")
        source("OkE2ETest.kt", "@PlaybackArea\nclass OkE2ETest {\n @Test fun a() {}\n}")

        tags.problems().shouldBeEmpty()
    }

    @Test
    fun `no tests at all is a problem`() {
        tags.problems().single() shouldContain "no e2e test classes"
    }
}
