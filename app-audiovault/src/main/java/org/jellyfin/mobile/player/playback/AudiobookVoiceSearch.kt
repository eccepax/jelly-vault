package org.jellyfin.mobile.player.playback

import org.jellyfin.mobile.player.interaction.PlayOptions
import org.jellyfin.sdk.api.client.exception.ApiClientException
import timber.log.Timber
import java.util.UUID

/** "Play [book] on AudioVault", through Assistant: the server's best match, ready to play. */
class AudiobookVoiceSearch(
    /** The id of the item that best matches what was asked for; null when nothing does. */
    private val topMatch: suspend (query: String) -> UUID?,
    private val loader: AudiobookQueueLoader,
) {
    suspend fun bookFor(query: String): AudiobookQueue? {
        val id = try {
            topMatch(query)
        } catch (e: ApiClientException) {
            Timber.w(e, "Couldn't search for %s", query)
            null
        } ?: return null
        val options = PlayOptions(
            ids = listOf(id),
            mediaSourceId = null,
            startIndex = 0,
            startPosition = null,
            audioStreamIndex = null,
            subtitleStreamIndex = null,
            playFromDownloads = false,
        )
        return loader.load(options).getOrNull()
    }
}
