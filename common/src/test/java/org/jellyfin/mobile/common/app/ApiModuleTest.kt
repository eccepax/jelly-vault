package org.jellyfin.mobile.common.app

import android.app.Application
import android.content.Context
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.data.dao.ServerDao
import org.jellyfin.mobile.common.data.dao.UserDao
import org.jellyfin.mobile.common.server.ServerSession
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.JellyfinOptions
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.ClientInfo
import org.junit.jupiter.api.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class ApiModuleTest {

    @Test
    fun `needs nothing from an app beyond its preferences, account tables and sign-out`() {
        apiModule(ClientInfo("test", "1")).verify(
            extraTypes = listOf(
                // Android
                Context::class,
                Application::class,
                // Built by the module itself through the SDK's factories, not constructors
                Jellyfin::class,
                JellyfinOptions::class,
                JellyfinOptions.Builder::class,
                ApiClient::class,
                // What each app provides
                AppPreferences::class,
                ServerDao::class,
                UserDao::class,
                SignOut::class,
            ),
        )
    }

    @Test
    fun `the plain sign-out ends the session, then logs out through the api client controller`() = runTest {
        val events = mutableListOf<String>()
        val controller = mockk<ApiClientController> { coEvery { logoutUser() } answers { events += "logout" } }
        val koin = koinApplication {
            modules(
                module {
                    single<ServerSession> { ServerSession { events += "end session" } }
                    single { controller }
                },
                plainSignOutModule,
            )
        }.koin

        koin.get<SignOut>()()

        events shouldBe listOf("end session", "logout")
    }
}
