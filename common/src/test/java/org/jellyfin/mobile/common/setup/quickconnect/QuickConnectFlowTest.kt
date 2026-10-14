package org.jellyfin.mobile.common.setup.quickconnect

import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class QuickConnectFlowTest {

    private class FakeQuickConnect : QuickConnect {
        var enabled = true
        var approved = false
        var failRequest = false
        var checks = 0
        override suspend fun isEnabled() = enabled
        override suspend fun request(): QuickConnectRequest {
            if (failRequest) throw IOException("offline")
            return QuickConnectRequest(code = "123456", secret = "s3cret")
        }
        override suspend fun isApproved(secret: String): Boolean {
            checks++
            return approved
        }
        override suspend fun signIn(secret: String) = SignedInUser(userId = "user-1", accessToken = "token-1")
    }

    private val server = FakeQuickConnect()

    private fun signIn() = quickConnectSignIn(server, pollIntervalMs = 5_000L, expiresAfterMs = 60_000L)

    @Test
    fun `shows the code to approve, then signs in once it's approved`() = runTest {
        signIn().test {
            awaitItem() shouldBe QuickConnectState.WaitingForApproval("123456")
            advanceTimeBy(12_000L)
            server.approved = true
            awaitItem() shouldBe QuickConnectState.SignedIn(SignedInUser("user-1", "token-1"))
            awaitComplete()
        }
    }

    @Test
    fun `checks every poll interval, not continuously`() = runTest {
        signIn().test {
            awaitItem()
            advanceTimeBy(16_000L)
            server.checks shouldBe 3
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `says so when the server has Quick Connect turned off`() = runTest {
        server.enabled = false
        signIn().test {
            awaitItem() shouldBe QuickConnectState.Unavailable
            awaitComplete()
        }
    }

    @Test
    fun `stops waiting when the code expires`() = runTest {
        signIn().test {
            awaitItem() shouldBe QuickConnectState.WaitingForApproval("123456")
            advanceTimeBy(61_000L)
            awaitItem() shouldBe QuickConnectState.Expired
            awaitComplete()
        }
    }

    @Test
    fun `reports a failure instead of crashing when the server can't be reached`() = runTest {
        server.failRequest = true
        signIn().test {
            awaitItem() shouldBe QuickConnectState.Failed
            awaitComplete()
        }
    }
}
