package org.jellyfin.mobile.common.setup

import org.jellyfin.mobile.common.setup.quickconnect.SignedInUser
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.userApi

/** Signs in with a username and password; null when the server's answer has no user or token. */
fun interface PasswordSignIn {
    suspend fun signIn(username: String, password: String): SignedInUser?
}

class JellyfinPasswordSignIn(private val apiClient: ApiClient) : PasswordSignIn {
    override suspend fun signIn(username: String, password: String): SignedInUser? {
        val result = apiClient.userApi.authenticateUserByName(username, password).content
        val userId = result.user?.id?.toString()
        val accessToken = result.accessToken
        return if (userId.isNullOrBlank() || accessToken.isNullOrBlank()) null else SignedInUser(userId, accessToken)
    }
}
