package org.jellyfin.mobile.livetv.account

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private var signOuts = 0
    private val signOutDone = CompletableDeferred<Unit>()
    private val viewModel by lazy {
        AccountViewModel(
            signOut = {
                signOuts++
                signOutDone.await()
            },
        )
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `signed in at first`() = runTest(dispatcher) {
        viewModel.signingOut.value shouldBe false
        viewModel.signedOut.value shouldBe false
    }

    @Test
    fun `signing out shows progress until it is done`() = runTest(dispatcher) {
        viewModel.signOut()
        runCurrent()

        viewModel.signingOut.value shouldBe true
        viewModel.signedOut.value shouldBe false

        signOutDone.complete(Unit)
        advanceUntilIdle()

        viewModel.signedOut.value shouldBe true
    }

    @Test
    fun `signing out twice signs out once`() = runTest(dispatcher) {
        viewModel.signOut()
        runCurrent()
        viewModel.signOut()
        signOutDone.complete(Unit)
        advanceUntilIdle()

        signOuts shouldBe 1
    }
}
