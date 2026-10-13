package org.jellyfin.mobile.downloads

import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

private val ITEM_ID_REGEX = Regex("""Items/([a-f0-9]{32})/Download""", RegexOption.IGNORE_CASE)

/**
 * Extracts the item id from a Jellyfin download URL (e.g. `.../Items/<32-hex-chars>/Download`).
 *
 * Returns null on no match, rather than the previous behavior of coercing a failed match to the
 * literal string `"null"` via Kotlin's `null.toString()`.
 */
internal fun extractItemId(downloadUrl: String): String? =
    ITEM_ID_REGEX.find(downloadUrl)?.groups?.get(1)?.value

/** Shows/movies/episodes and audio content download to internal storage when the preference is enabled. */
internal fun isDestinationInternal(downloadToInternal: Boolean?, itemType: BaseItemKind?): Boolean =
    downloadToInternal == true && itemType in listOf(
        BaseItemKind.EPISODE,
        BaseItemKind.MOVIE,
        BaseItemKind.VIDEO,
        BaseItemKind.AUDIO_BOOK,
        BaseItemKind.AUDIO,
    )

/**
 * Builds a Jellyfin item-download URL in the dashless 32-hex-char form [extractItemId] expects
 * back out of it (`.../Items/<32-hex-chars>/Download?api_key=...`).
 */
internal fun buildDownloadUrl(baseUrl: String, accessToken: String?, itemId: UUID): String {
    val hexId = itemId.toString().replace("-", "")
    return "${baseUrl.trimEnd('/')}/Items/$hexId/Download?api_key=${accessToken.orEmpty()}"
}

/**
 * WIFI_ONLY requires an unmetered connection; MOBILE_DATA rejects roaming networks;
 * MOBILE_AND_ROAMING (or any other value) allows anything.
 */
internal fun isValidDownloadConnection(
    downloadMethod: Int,
    isNetworkMetered: Boolean,
    isNetworkRoaming: Boolean,
): Boolean =
    when (downloadMethod) {
        DownloadMethod.WIFI_ONLY -> !isNetworkMetered
        DownloadMethod.MOBILE_DATA -> !isNetworkRoaming
        else -> true
    }
