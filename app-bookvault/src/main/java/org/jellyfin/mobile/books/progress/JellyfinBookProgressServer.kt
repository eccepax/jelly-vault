package org.jellyfin.mobile.books.progress

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.common.server.JellyfinServerPositions
import org.jellyfin.mobile.common.server.ServerPositions
import org.jellyfin.mobile.common.server.toServerDateTime
import org.jellyfin.mobile.common.server.toServerInstant
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import org.jellyfin.sdk.model.extensions.inWholeTicks
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration

/** [BookProgressServer] in the signed-in user's Jellyfin play data, as the other apps keep theirs. */
class JellyfinBookProgressServer(
    private val apiClient: ApiClient,
) : BookProgressServer, ServerPositions by JellyfinServerPositions(apiClient) {

    override suspend fun progress(bookIds: Collection<UUID>): Map<UUID, ServerBookProgress> =
        withContext(Dispatchers.IO) {
            apiClient.itemsApi.getItems(ids = bookIds, enableUserData = true).content.items.orEmpty()
                .mapNotNull { book -> book.id?.let { it to book.serverProgress() } }
                .toMap()
        }

    /**
     * Unlike a track, a book keeps its place when finished, so it opens on its last page; and a place
     * partway through is unplayed, so a finished book read again from the start is unread again.
     */
    override suspend fun record(itemId: UUID, position: Duration, finished: Boolean, at: Instant) {
        withContext(Dispatchers.IO) {
            apiClient.itemsApi.updateItemUserData(
                itemId = itemId,
                data = UpdateUserItemDataDto(
                    playbackPositionTicks = position.inWholeTicks,
                    played = finished,
                    lastPlayedDate = at.toServerDateTime(),
                ),
            )
        }
    }
}

/** The reader's place in this book, as the server sent it with the book. */
fun BaseItemDto.serverProgress() = ServerBookProgress(
    positionTicks = userData?.playbackPositionTicks ?: 0L,
    runTimeTicks = runTimeTicks,
    played = userData?.played == true,
    lastPlayed = userData?.lastPlayedDate?.toServerInstant(),
)
