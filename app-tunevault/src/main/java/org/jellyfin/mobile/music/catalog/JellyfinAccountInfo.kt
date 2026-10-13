package org.jellyfin.mobile.music.catalog

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi

class JellyfinAccountInfo(
    private val apiClient: ApiClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AccountInfo {
    override suspend fun current(): Account = withContext(ioDispatcher) {
        coroutineScope {
            val user = async { apiClient.userApi.getCurrentUser().content }
            val server = async { apiClient.systemApi.getPublicSystemInfo().content }
            Account(
                userName = user.await().name.orEmpty(),
                serverName = server.await().serverName.orEmpty(),
                serverUrl = apiClient.baseUrl.orEmpty(),
            )
        }
    }
}
