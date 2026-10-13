package org.jellyfin.mobile.common

import android.app.Application
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.app.ApiClientController
import org.jellyfin.mobile.common.data.entity.ServerEntity
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val events = mutableListOf<String>()
    private val apiClientController = mockk<ApiClientController>(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { apiClientController.loadSavedServer() } returns ServerEntity("http://vault")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = MainViewModel(
        mockk<Application>(relaxed = true),
        apiClientController,
        SignOut(session = { events += "end session" }, logout = { events += "logout" }),
    )

    @Test
    fun `logging out signs out on the server too, not just on this device`() = runTest {
        viewModel().logout()

        events shouldBe listOf("end session", "logout")
    }

    @Test
    fun `logging out refreshes the server state afterwards`() = runTest {
        val viewModel = viewModel()
        coEvery { apiClientController.loadSavedServer() } returns null

        viewModel.logout()

        viewModel.serverState.value shouldBe ServerState.Unset
    }
}
