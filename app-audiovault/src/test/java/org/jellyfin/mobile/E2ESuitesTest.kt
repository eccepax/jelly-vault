package org.jellyfin.mobile

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import org.jellyfin.mobile.testsupport.E2ESuiteTags
import org.junit.jupiter.api.Test
import java.io.File

/** Guards the e2e suite tags (androidTest e2e/E2ESuites.kt); see [E2ESuiteTags]. */
class E2ESuitesTest {

    private val tags = E2ESuiteTags(
        e2eDir = File("src/androidTest/java/org/jellyfin/mobile/e2e"),
        areas = listOf("BrowseArea", "PlaybackArea", "AccountArea", "DownloadsArea"),
    )

    @Test
    fun `every e2e test class is in exactly one area, and smoke tags single tests`() {
        tags.problems().shouldBeEmpty()
    }

    @Test
    fun `there is a smoke suite`() {
        tags.smokeTests().shouldNotBeEmpty()
    }
}
