package org.jellyfin.mobile.utils

import android.net.Uri
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.events.DownloadFileSpec
import org.junit.jupiter.api.Test

/**
 * Regression coverage for [downloadAllCatchingFailures] — extracted from the multi-track download
 * loop after a bug where each track's download request independently re-prompted for the
 * network-type preference and notification permission (the loop itself wasn't the cause, but this
 * locks down the "one failure doesn't abort the batch" behavior the fix relies on).
 */
class SystemUtilsTest {

    private fun file(name: String) = DownloadFileSpec(mockk<Uri>(relaxed = true), name)

    @Test
    fun `an empty file list never invokes the download action`() = runTest {
        var callCount = 0

        downloadAllCatchingFailures(emptyList()) { callCount++ }

        callCount shouldBe 0
    }

    @Test
    fun `every file is downloaded when none fail`() = runTest {
        val downloaded = mutableListOf<String>()

        downloadAllCatchingFailures(listOf(file("a"), file("b"), file("c"))) { downloaded += it.filename }

        downloaded shouldContainExactly listOf("a", "b", "c")
    }

    @Test
    fun `one file failing does not stop the rest of the batch from being attempted`() = runTest {
        val attempted = mutableListOf<String>()

        downloadAllCatchingFailures(listOf(file("a"), file("bad"), file("c"))) { spec ->
            attempted += spec.filename
            if (spec.filename == "bad") error("simulated download failure")
        }

        attempted shouldContainExactly listOf("a", "bad", "c")
    }

    @Test
    fun `a failure on the very last file does not propagate out of the batch`() = runTest {
        downloadAllCatchingFailures(listOf(file("a"))) { error("simulated download failure") }
        // No exception thrown above means the failure was caught rather than propagating.
    }
}
