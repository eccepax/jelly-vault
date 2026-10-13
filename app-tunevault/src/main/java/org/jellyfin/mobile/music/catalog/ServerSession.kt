package org.jellyfin.mobile.music.catalog

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.sessionApi

/** The signed-in session on the server. */
fun interface ServerSession {
    /** Signs out on the server, revoking this session's token. */
    suspend fun end()
}

class JellyfinServerSession(
    private val apiClient: ApiClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ServerSession {
    override suspend fun end() {
        withContext(ioDispatcher) { apiClient.sessionApi.reportSessionEnded() }
    }
}
