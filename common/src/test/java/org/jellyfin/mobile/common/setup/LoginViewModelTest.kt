package org.jellyfin.mobile.common.setup

import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.app.ApiClientController
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.setup.quickconnect.QuickConnect
import org.jellyfin.mobile.common.setup.quickconnect.QuickConnectRequest
import org.jellyfin.mobile.common.setup.quickconnect.SignedInUser
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val passwordSignIn = mockk<PasswordSignIn>()
    private val quickConnect = mockk<QuickConnect>()
    private val apiClientController = mockk<ApiClientController>(relaxed = true)
    private val appPreferences = mockk<AppPreferences>()
    private var signedIn = 0

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { appPreferences.currentServerId } returns SERVER_ID
        coEvery { passwordSignIn.signIn(any(), any()) } returns SignedInUser("alice-id", "token")
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = LoginViewModel(
        passwordSignIn,
        quickConnect,
        apiClientController,
        appPreferences,
        dispatcher,
    )

    @Test
    fun `signing in saves the user for the current server, then says so`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.signIn(" alice ", "secret", onSignedIn = { signedIn++ })

        coVerify { passwordSignIn.signIn("alice", "secret") }
        coVerify { apiClientController.setupUser(SERVER_ID, "alice-id", "token") }
        signedIn shouldBe 1
        viewModel.state.value shouldBe LoginUiState()
    }

    @Test
    fun `without a username, it asks for one and doesn't try`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.signIn("  ", "secret", onSignedIn = { signedIn++ })

        viewModel.state.value.error shouldBe LoginMessage(R.string.login_error_missing_username)
        coVerify(exactly = 0) { passwordSignIn.signIn(any(), any()) }
    }

    @Test
    fun `without a server, it says so`() = runTest(dispatcher) {
        every { appPreferences.currentServerId } returns null
        val viewModel = viewModel()

        viewModel.signIn("alice", "secret", onSignedIn = { signedIn++ })

        viewModel.state.value.error shouldBe LoginMessage(R.string.login_error_missing_server)
    }

    @Test
    fun `while signing in it's busy`() = runTest(dispatcher) {
        coEvery { passwordSignIn.signIn(any(), any()) } coAnswers { awaitCancellation() }
        val viewModel = viewModel()

        viewModel.signIn("alice", "secret", onSignedIn = { signedIn++ })

        viewModel.state.value.busy shouldBe true
    }

    @Test
    fun `an answer without a user or token is a failed sign-in`() = runTest(dispatcher) {
        coEvery { passwordSignIn.signIn(any(), any()) } returns null
        val viewModel = viewModel()

        viewModel.signIn("alice", "secret", onSignedIn = { signedIn++ })

        viewModel.state.value shouldBe LoginUiState(error = LoginMessage(R.string.login_error_invalid_response))
        signedIn shouldBe 0
    }

    @Test
    fun `when the server refuses, it shows why`() = runTest(dispatcher) {
        coEvery { passwordSignIn.signIn(any(), any()) } throws ApiClientException("Invalid username or password")
        val viewModel = viewModel()

        viewModel.signIn("alice", "wrong", onSignedIn = { signedIn++ })

        viewModel.state.value shouldBe
            LoginUiState(error = LoginMessage(R.string.login_error_failed, "Invalid username or password"))
    }

    @Test
    fun `a server that never answers times out`() = runTest(dispatcher) {
        coEvery { passwordSignIn.signIn(any(), any()) } coAnswers { awaitCancellation() }
        val viewModel = viewModel()

        viewModel.signIn("alice", "secret", onSignedIn = { signedIn++ })
        advanceTimeBy(LOGIN_TIMEOUT_MS + 1)

        viewModel.state.value shouldBe LoginUiState(error = LoginMessage(R.string.login_error_timeout))
    }

    @Test
    fun `quick connect shows the code to approve`() = runTest(dispatcher) {
        coEvery { quickConnect.isEnabled() } returns true
        coEvery { quickConnect.request() } returns QuickConnectRequest("123456", "secret")
        coEvery { quickConnect.isApproved(any()) } returns false
        val viewModel = viewModel()

        viewModel.startQuickConnect(onSignedIn = { signedIn++ })

        viewModel.state.value.quickConnectCode shouldBe "123456"
    }

    @Test
    fun `once the code is approved, quick connect signs in`() = runTest(dispatcher) {
        coEvery { quickConnect.isEnabled() } returns true
        coEvery { quickConnect.request() } returns QuickConnectRequest("123456", "secret")
        coEvery { quickConnect.isApproved("secret") } returns true
        coEvery { quickConnect.signIn("secret") } returns SignedInUser("bob-id", "qc-token")
        val viewModel = viewModel()

        viewModel.startQuickConnect(onSignedIn = { signedIn++ })
        advanceTimeBy(QUICK_CONNECT_WAIT_MS)

        coVerify { apiClientController.setupUser(SERVER_ID, "bob-id", "qc-token") }
        signedIn shouldBe 1
    }

    @Test
    fun `quick connect turned off on the server says so`() = runTest(dispatcher) {
        coEvery { quickConnect.isEnabled() } returns false
        val viewModel = viewModel()

        viewModel.startQuickConnect(onSignedIn = { signedIn++ })

        viewModel.state.value shouldBe
            LoginUiState(error = LoginMessage(R.string.login_quick_connect_unavailable))
    }

    private fun waitingForApproval() {
        coEvery { quickConnect.isEnabled() } returns true
        coEvery { quickConnect.request() } returns QuickConnectRequest("123456", "secret")
        coEvery { quickConnect.isApproved(any()) } returns false
    }

    @Test
    fun `signing in with a password stops waiting for quick connect`() = runTest(dispatcher) {
        waitingForApproval()
        coEvery { passwordSignIn.signIn(any(), any()) } throws ApiClientException("Invalid username or password")
        val viewModel = viewModel()

        viewModel.startQuickConnect(onSignedIn = { signedIn++ })
        viewModel.signIn("alice", "wrong", onSignedIn = { signedIn++ })
        advanceTimeBy(QUICK_CONNECT_EXPIRES_MS * 2)

        // The password's error stays, not overwritten by the code expiring out of sight
        viewModel.state.value shouldBe
            LoginUiState(error = LoginMessage(R.string.login_error_failed, "Invalid username or password"))
        coVerify(exactly = 0) { quickConnect.isApproved(any()) }
    }

    @Test
    fun `leaving the screen stops waiting for quick connect`() = runTest(dispatcher) {
        waitingForApproval()
        val viewModel = viewModel()

        viewModel.startQuickConnect(onSignedIn = { signedIn++ })
        viewModel.leave()
        advanceTimeBy(QUICK_CONNECT_EXPIRES_MS * 2)

        viewModel.state.value shouldBe LoginUiState()
        coVerify(exactly = 0) { quickConnect.isApproved(any()) }
    }

    @Test
    fun `leaving the screen forgets the last error, so it isn't there next time`() = runTest(dispatcher) {
        coEvery { passwordSignIn.signIn(any(), any()) } throws ApiClientException("Invalid username or password")
        val viewModel = viewModel()

        viewModel.signIn("alice", "wrong", onSignedIn = { signedIn++ })
        viewModel.leave()

        viewModel.state.value shouldBe LoginUiState()
    }

    private companion object {
        const val SERVER_ID = 7L
        const val QUICK_CONNECT_WAIT_MS = 10_000L
        const val QUICK_CONNECT_EXPIRES_MS = 10 * 60_000L
    }
}
