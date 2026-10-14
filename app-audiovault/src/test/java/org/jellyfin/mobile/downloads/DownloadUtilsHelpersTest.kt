package org.jellyfin.mobile.downloads

import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.common.server.itemDownloadUrl
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Regression coverage for a bug found while writing these tests: the item-id regex only matched
 * lowercase hex, so any download URL containing uppercase hex (the item id casing Jellyfin
 * actually returns is not guaranteed lowercase) would silently fail to match, and the un-fixed
 * caller coerced that failed match to the literal string `"null"` via Kotlin's `null.toString()`
 * rather than a real null.
 */
class DownloadUtilsHelpersTest {

    @Test
    fun `extracts the item id from a typical lowercase-hex download URL`() {
        extractItemId("https://server/Items/ded9d452b0effd8a13f978d5ba92d1b6/Download") shouldBe "ded9d452b0effd8a13f978d5ba92d1b6"
    }

    @Test
    fun `extracts the item id when it contains uppercase hex`() {
        extractItemId("https://server/Items/DED9D452B0EFFD8A13F978D5BA92D1B6/Download") shouldBe "DED9D452B0EFFD8A13F978D5BA92D1B6"
    }

    @Test
    fun `extracts the item id from mixed-case hex`() {
        extractItemId("https://server/Items/Ded9d452B0efFD8a13f978d5BA92d1b6/Download") shouldBe "Ded9d452B0efFD8a13f978d5BA92d1b6"
    }

    @Test
    fun `returns null (not the literal string null) for a malformed URL`() {
        extractItemId("https://server/not-a-valid-download-url") shouldBe null
    }

    @Test
    fun `internal destination is true for audio and video-like types when the preference is enabled`() {
        for (type in listOf(
            BaseItemKind.AUDIO_BOOK,
            BaseItemKind.AUDIO,
            BaseItemKind.MOVIE,
            BaseItemKind.EPISODE,
            BaseItemKind.VIDEO,
        )) {
            isDestinationInternal(downloadToInternal = true, itemType = type) shouldBe true
        }
    }

    @Test
    fun `internal destination is false for an unsupported type even when the preference is enabled`() {
        isDestinationInternal(downloadToInternal = true, itemType = BaseItemKind.BOOK) shouldBe false
    }

    @Test
    fun `internal destination is false when the preference is disabled regardless of type`() {
        isDestinationInternal(downloadToInternal = false, itemType = BaseItemKind.AUDIO_BOOK) shouldBe false
        isDestinationInternal(downloadToInternal = null, itemType = BaseItemKind.AUDIO_BOOK) shouldBe false
    }

    // --- itemDownloadUrl round trip ---------------------------------------------------------------

    @Test
    fun `the shared download URL builder produces ids extractItemId can parse back out`() {
        val itemId = UUID.fromString("ded9d452-b0ef-fd8a-13f9-78d5ba92d1b6")

        extractItemId(itemDownloadUrl("https://server", itemId, "tok")) shouldBe "ded9d452b0effd8a13f978d5ba92d1b6"
        extractItemId(itemDownloadUrl("https://server", itemId, null)) shouldBe "ded9d452b0effd8a13f978d5ba92d1b6"
    }

    // --- isValidDownloadConnection ---------------------------------------------------------------

    @Test
    fun `WIFI_ONLY requires an unmetered connection`() {
        isValidDownloadConnection(DownloadMethod.WIFI_ONLY, isNetworkMetered = false, isNetworkRoaming = false) shouldBe true
        isValidDownloadConnection(DownloadMethod.WIFI_ONLY, isNetworkMetered = true, isNetworkRoaming = false) shouldBe false
    }

    @Test
    fun `WIFI_ONLY ignores roaming state entirely`() {
        // A metered check failure should reject even if (implausibly) marked as not roaming.
        isValidDownloadConnection(DownloadMethod.WIFI_ONLY, isNetworkMetered = true, isNetworkRoaming = true) shouldBe false
    }

    @Test
    fun `MOBILE_DATA rejects a roaming network`() {
        isValidDownloadConnection(DownloadMethod.MOBILE_DATA, isNetworkMetered = true, isNetworkRoaming = true) shouldBe false
    }

    @Test
    fun `MOBILE_DATA allows a metered but non-roaming network`() {
        isValidDownloadConnection(DownloadMethod.MOBILE_DATA, isNetworkMetered = true, isNetworkRoaming = false) shouldBe true
    }

    @Test
    fun `MOBILE_AND_ROAMING allows any connection`() {
        isValidDownloadConnection(DownloadMethod.MOBILE_AND_ROAMING, isNetworkMetered = true, isNetworkRoaming = true) shouldBe true
        isValidDownloadConnection(DownloadMethod.MOBILE_AND_ROAMING, isNetworkMetered = false, isNetworkRoaming = false) shouldBe true
    }
}
