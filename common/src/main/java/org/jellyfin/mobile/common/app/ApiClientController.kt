package org.jellyfin.mobile.common.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.common.data.dao.ServerDao
import org.jellyfin.mobile.common.data.dao.UserDao
import org.jellyfin.mobile.common.data.entity.ServerEntity
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.DeviceInfo
import timber.log.Timber

class ApiClientController(
    private val appPreferences: AppPreferences,
    private val jellyfin: Jellyfin,
    private val apiClient: ApiClient,
    private val serverDao: ServerDao,
    private val userDao: UserDao,
) {
    private val baseDeviceInfo: DeviceInfo
        get() = checkNotNull(jellyfin.options.deviceInfo) {
            "Jellyfin SDK was not initialized with a DeviceInfo — check AppModule Koin setup"
        }

    suspend fun setupServer(hostname: String) {
        appPreferences.currentServerId = withContext(Dispatchers.IO) {
            serverDao.getServerByHostname(hostname)?.id ?: serverDao.insert(hostname)
        }
        logoutUser()
        apiClient.update(baseUrl = hostname)
    }

    suspend fun setupUser(serverId: Long, userId: String, accessToken: String) {
        appPreferences.currentUserId = withContext(Dispatchers.IO) {
            userDao.upsert(serverId, userId, accessToken)
        }
        configureApiClientUser(userId, accessToken)
    }

    suspend fun loadSavedServer(): ServerEntity? {
        val server = withContext(Dispatchers.IO) {
            val serverId = appPreferences.currentServerId ?: return@withContext null
            serverDao.getServer(serverId)
        }
        configureApiClientServer(server)
        return server
    }

    suspend fun loadSavedServerUser() {
        val serverId = appPreferences.currentServerId ?: return
        val userDbId = appPreferences.currentUserId ?: run {
            resetApiClientUser()
            return
        }

        val serverUser = withContext(Dispatchers.IO) {
            userDao.getServerUser(serverId, userDbId)
        } ?: run {
            resetApiClientUser()
            return
        }

        val token = serverUser.user.accessToken
        if (token != null) {
            configureApiClientUser(serverUser.user.userId, token)
            if (!isSessionValid()) {
                // Token is stale/revoked (e.g. restored from an Android backup onto a new device
                // after the server invalidated the session) — fall back to the login screen
                // instead of leaving the UI stuck issuing 401s against every request.
                logoutUser()
            }
        } else {
            // A user with no token can't be signed in (e.g. the token was forgotten but the app
            // died before the signed-in user was): back to sign-in rather than 401s.
            logoutUser()
        }
    }

    /**
     * Verifies the currently configured access token still works, via a cheap authenticated call.
     * Only treats 401/403 as an invalid session — network/server errors return true so a temporary
     * outage doesn't log the user out.
     */
    private suspend fun isSessionValid(): Boolean = withContext(Dispatchers.IO) {
        try {
            apiClient.userApi.getCurrentUser()
            true
        } catch (e: InvalidStatusException) {
            e.status != HTTP_STATUS_UNAUTHORIZED && e.status != HTTP_STATUS_FORBIDDEN
        } catch (e: ApiClientException) {
            true
        }
    }

    private companion object {
        const val HTTP_STATUS_UNAUTHORIZED = 401
        const val HTTP_STATUS_FORBIDDEN = 403
    }

    suspend fun loadPreviouslyUsedServers(): List<ServerEntity> = withContext(Dispatchers.IO) {
        serverDao.getAllServers().filterNot { server ->
            server.id == appPreferences.currentServerId
        }
    }

    private fun configureApiClientServer(server: ServerEntity?) {
        apiClient.update(baseUrl = server?.hostname)
    }

    private fun configureApiClientUser(userId: String, accessToken: String) {
        apiClient.update(
            accessToken = accessToken,
            deviceInfo = baseDeviceInfo.copy(id = baseDeviceInfo.id + userId),
        )
    }

    /**
     * Forgets the signed-in user on this device, including the saved token: if the server couldn't
     * revoke it (offline, no answer), it mustn't stay on the device still working.
     */
    suspend fun logoutUser() {
        // Signed out in memory first, so a failed or interrupted write below can't leave the app
        // signed in with no token (or a revoked one).
        val userDbId = appPreferences.currentUserId
        appPreferences.currentUserId = null
        resetApiClientUser()
        if (userDbId != null) forgetToken(userDbId)
    }

    /** Runs to the end even if the caller is cancelled; a failure is logged, not thrown. */
    private suspend fun forgetToken(userDbId: Long) = withContext(NonCancellable + Dispatchers.IO) {
        try {
            userDao.logout(userDbId)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Timber.e(e, "Couldn't forget the saved token")
        }
    }

    private fun resetApiClientUser() {
        apiClient.update(
            accessToken = null,
            deviceInfo = baseDeviceInfo,
        )
    }
}
