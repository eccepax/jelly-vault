package org.jellyfin.mobile.downloads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.common.server.toServerDateTime
import org.jellyfin.mobile.common.server.toServerInstant
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import org.jellyfin.sdk.model.extensions.inWholeTicks
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration

/** [ServerPositions] in the signed-in user's Jellyfin play data. */
class JellyfinServerPositions(private val apiClient: ApiClient) : ServerPositions {

    override suspend fun lastPlayed(itemId: UUID): Instant? = withContext(Dispatchers.IO) {
        apiClient.itemsApi.getItemUserData(itemId).content.lastPlayedDate?.toServerInstant()
    }

    override suspend fun record(itemId: UUID, position: Duration, finished: Boolean, at: Instant) {
        withContext(Dispatchers.IO) {
            apiClient.itemsApi.updateItemUserData(
                itemId = itemId,
                data = UpdateUserItemDataDto(
                    playbackPositionTicks = if (finished) 0L else position.inWholeTicks,
                    // Partway through leaves "played" as it was: a book's resume point is its first unplayed track
                    played = if (finished) true else null,
                    lastPlayedDate = at.toServerDateTime(),
                ),
            )
        }
    }
}
