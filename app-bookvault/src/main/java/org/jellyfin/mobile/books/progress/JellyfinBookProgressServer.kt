package org.jellyfin.mobile.books.progress

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.common.server.JellyfinServerPositions
import org.jellyfin.mobile.common.server.ServerPositions
import org.jellyfin.mobile.common.server.toServerInstant
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

/** [BookProgressServer] in the signed-in user's Jellyfin play data, as the other apps keep theirs. */
class JellyfinBookProgressServer(
    private val apiClient: ApiClient,
) : BookProgressServer, ServerPositions by JellyfinServerPositions(apiClient) {

    override suspend fun progress(bookId: UUID): ServerBookProgress = withContext(Dispatchers.IO) {
        apiClient.userLibraryApi.getItem(bookId).content.serverProgress()
    }
}

/** The reader's place in this book, as the server sent it with the book. */
fun BaseItemDto.serverProgress() = ServerBookProgress(
    positionTicks = userData?.playbackPositionTicks ?: 0L,
    runTimeTicks = runTimeTicks,
    played = userData?.played == true,
    lastPlayed = userData?.lastPlayedDate?.toServerInstant(),
)
