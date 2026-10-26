package org.jellyfin.mobile.common.account

import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SigningOutTest {

    private var signOuts = 0
    private var fails = false
    private var finishes: CompletableDeferred<Unit>? = null

    private fun TestScope.signingOut() = SigningOut(backgroundScope) {
        signOuts++
        finishes?.await()
        if (fails) error("Couldn't forget the account")
    }

    @Test
    fun `signed in at first`() = runTest(UnconfinedTestDispatcher()) {
        val signingOut = signingOut()

        signingOut.inProgress.value shouldBe false
        signingOut.done.value shouldBe false
    }

    @Test
    fun `shows it's signing out until it's done`() = runTest(UnconfinedTestDispatcher()) {
        finishes = CompletableDeferred()
        val signingOut = signingOut()

        signingOut.start()
        signingOut.inProgress.value shouldBe true
        signingOut.done.value shouldBe false

        finishes!!.complete(Unit)
        signingOut.done.value shouldBe true
    }

    @Test
    fun `asked again while signing out, it still signs out once`() = runTest(UnconfinedTestDispatcher()) {
        finishes = CompletableDeferred()
        val signingOut = signingOut()

        signingOut.start()
        signingOut.start()
        finishes!!.complete(Unit)

        signOuts shouldBe 1
    }

    @Test
    fun `a sign out that fails says so, and can be tried again`() = runTest(UnconfinedTestDispatcher()) {
        fails = true
        val signingOut = signingOut()

        signingOut.failures.test {
            signingOut.start()
            awaitItem()
        }
        signingOut.inProgress.value shouldBe false
        signingOut.done.value shouldBe false

        fails = false
        signingOut.start()

        signOuts shouldBe 2
        signingOut.done.value shouldBe true
    }
}
