package org.jellyfin.mobile.common.setup.quickconnect

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.QuickConnectDto

/** [QuickConnect] over Jellyfin's API, for a client that isn't signed in yet. */
class JellyfinQuickConnect(
    private val apiClient: ApiClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : QuickConnect {

    override suspend fun isEnabled(): Boolean = withContext(ioDispatcher) {
        apiClient.quickConnectApi.getQuickConnectEnabled().content
    }

    override suspend fun request(): QuickConnectRequest = withContext(ioDispatcher) {
        val result = apiClient.quickConnectApi.initiateQuickConnect().content
        QuickConnectRequest(code = result.code, secret = result.secret)
    }

    override suspend fun isApproved(secret: String): Boolean = withContext(ioDispatcher) {
        apiClient.quickConnectApi.getQuickConnectState(secret).content.authenticated
    }

    override suspend fun signIn(secret: String): SignedInUser = withContext(ioDispatcher) {
        val result = apiClient.userApi.authenticateWithQuickConnect(QuickConnectDto(secret)).content
        SignedInUser(
            userId = requireNotNull(result.user?.id) { "No user in the sign-in result" }.toString(),
            accessToken = requireNotNull(result.accessToken) { "No access token in the sign-in result" },
        )
    }
}
