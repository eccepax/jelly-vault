package org.jellyfin.mobile.common.downloads

import app.cash.turbine.test
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.testing.downloads.FakeDownloadSettings
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsInSettingsTest {

    private val settings = FakeDownloadSettings()

    private fun TestScope.inSettings() = DownloadsInSettings(backgroundScope, settings)

    @Test
    fun `the space isn't known until the downloads say, then follows them`() =
        runTest(UnconfinedTestDispatcher()) {
            val downloads = inSettings()
            downloads.space.value shouldBe null

            downloads.space.filterNotNull().test {
                awaitItem() shouldBe DownloadSpace(bytes = 0L, hasDownloads = false)
                settings.bytesUsed.value = 1_500L
                expectMostRecentItem() shouldBe DownloadSpace(bytes = 1_500L, hasDownloads = true)
            }
        }

    @Test
    fun `downloads aren't followed while no screen shows them`() = runTest(UnconfinedTestDispatcher()) {
        val downloads = inSettings()
        val shown = downloads.space.launchIn(backgroundScope)
        settings.bytesUsed.subscriptionCount.value shouldBeGreaterThan 0

        shown.cancel()
        advanceTimeBy(DownloadsInSettings.STOP_FOLLOWING_AFTER_MS + 1)

        settings.bytesUsed.subscriptionCount.value shouldBe 0
    }

    @Test
    fun `removing them all removes them`() = runTest(UnconfinedTestDispatcher()) {
        inSettings().removeAll()

        settings.events shouldBe listOf("removeAll")
    }

    @Test
    fun `removing them all failing says so, and can be tried again`() = runTest(UnconfinedTestDispatcher()) {
        settings.removeFails = true
        val downloads = inSettings()

        downloads.removeFailures.test {
            downloads.removeAll()
            awaitItem()

            settings.removeFails = false
            downloads.removeAll()
            expectNoEvents()
        }
        settings.events shouldBe listOf("removeAll")
    }

    @Test
    fun `a failure while no screen shows them isn't said later`() = runTest(UnconfinedTestDispatcher()) {
        settings.removeFails = true
        val downloads = inSettings()
        downloads.removeAll()

        downloads.removeFailures.test { expectNoEvents() }
    }
}
