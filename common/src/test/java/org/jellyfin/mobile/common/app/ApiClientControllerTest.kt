package org.jellyfin.mobile.common.app

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.common.data.dao.ServerDao
import org.jellyfin.mobile.common.data.dao.UserDao
import org.jellyfin.mobile.common.data.entity.ServerEntity
import org.jellyfin.mobile.common.data.entity.ServerUser
import org.jellyfin.mobile.common.data.entity.UserEntity
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.JellyfinOptions
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.operations.UserApi
import org.jellyfin.sdk.model.DeviceInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Regression coverage for a fix where an access token restored onto a new device (e.g. via
 * Android's app-data auto-backup) but no longer honored by the server left the app stuck issuing
 * 401s against every request instead of falling back to the login screen.
 */
class ApiClientControllerTest {

    private val appPreferences = mockk<AppPreferences>(relaxed = true)
    private val jellyfin = mockk<Jellyfin>()
    private val apiClient = mockk<ApiClient>(relaxed = true)
    private val serverDao = mockk<ServerDao>()
    private val userDao = mockk<UserDao>()
    private val userApi = mockk<UserApi>()

    private val controller = ApiClientController(appPreferences, jellyfin, apiClient, serverDao, userDao)

    private val serverUser = ServerUser(
        user = UserEntity(
            id = 1L,
            serverId = 1L,
            userId = "user-1",
            accessToken = "stale-token",
            lastLoginTimestamp = 0L,
        ),
        server = ServerEntity(id = 1L, hostname = "https://example.com", lastUsedTimestamp = 0L),
    )

    @BeforeEach
    fun setUp() {
        mockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
        every { apiClient.userApi } returns userApi

        val jellyfinOptions = mockk<JellyfinOptions>()
        every { jellyfin.options } returns jellyfinOptions
        every { jellyfinOptions.deviceInfo } returns DeviceInfo(id = "device-id", name = "Test Device")

        every { appPreferences.currentServerId } returns 1L
        every { appPreferences.currentUserId } returns 1L
        every { userDao.getServerUser(1L, 1L) } returns serverUser
        every { userDao.logout(any()) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("org.jellyfin.sdk.api.client.extensions.ApiClientExtensionsKt")
    }

    @Test
    fun `keeps the session when the saved token is still valid`() = runTest {
        coEvery { userApi.getCurrentUser() } returns mockk(relaxed = true)

        controller.loadSavedServerUser()

        verify(exactly = 0) { appPreferences.currentUserId = null }
    }

    @Test
    fun `logs out when the saved token is rejected with 401`() = runTest {
        coEvery { userApi.getCurrentUser() } throws InvalidStatusException(401, null)

        controller.loadSavedServerUser()

        verify { appPreferences.currentUserId = null }
        verify { userDao.logout(1L) }
    }

    @Test
    fun `logs out when the saved token is rejected with 403`() = runTest {
        coEvery { userApi.getCurrentUser() } throws InvalidStatusException(403, null)

        controller.loadSavedServerUser()

        verify { appPreferences.currentUserId = null }
        verify { userDao.logout(1L) }
    }

    @Test
    fun `does not log out on a network or server error unrelated to authorization`() = runTest {
        // A transient outage (e.g. offline, timeout, 5xx) must not be treated as an invalid
        // session — that would log the user out just because the server was briefly unreachable.
        coEvery { userApi.getCurrentUser() } throws ApiClientException()

        controller.loadSavedServerUser()

        verify(exactly = 0) { appPreferences.currentUserId = null }
    }

    @Test
    fun `still configures the api client with the saved token before validating it`() = runTest {
        coEvery { userApi.getCurrentUser() } returns mockk(relaxed = true)

        controller.loadSavedServerUser()

        coVerify { apiClient.update(accessToken = "stale-token", deviceInfo = any()) }
    }

    @Test
    fun `signing out forgets the saved token, so one the server never revoked isn't left on the device`() = runTest {
        controller.logoutUser()

        verify { userDao.logout(1L) }
        verify { appPreferences.currentUserId = null }
    }

    @Test
    fun `signing out with nobody signed in touches no saved user`() = runTest {
        every { appPreferences.currentUserId } returns null

        controller.logoutUser()

        verify(exactly = 0) { userDao.logout(any()) }
    }

    @Test
    fun `a saved user with no token is signed out, not left signed in issuing 401s`() = runTest {
        every { userDao.getServerUser(1L, 1L) } returns serverUser.copy(
            user = serverUser.user.copy(accessToken = null),
        )

        controller.loadSavedServerUser()

        verify { appPreferences.currentUserId = null }
    }

    @Test
    fun `signing out still signs out when the saved token can't be forgotten`() = runTest {
        every { userDao.logout(any()) } throws IllegalStateException("database is locked")

        controller.logoutUser()

        verify { appPreferences.currentUserId = null }
        verify { apiClient.update(accessToken = null, deviceInfo = any()) }
    }

    @Test
    fun `signing out is signed out in memory before the saved token is forgotten`() = runTest {
        controller.logoutUser()

        verifyOrder {
            appPreferences.currentUserId = null
            userDao.logout(1L)
        }
    }

    @Test
    fun `switching server forgets the previous user's saved token`() = runTest {
        every { serverDao.getServerByHostname("https://other.example") } returns null
        every { serverDao.insert("https://other.example") } returns 2L

        controller.setupServer("https://other.example")

        verify { userDao.logout(1L) }
        verify { appPreferences.currentUserId = null }
    }
}
