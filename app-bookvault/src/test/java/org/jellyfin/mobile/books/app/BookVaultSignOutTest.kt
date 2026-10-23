package org.jellyfin.mobile.books.app

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** BookVault's own sign-out steps; the shared sequence is covered by common's SignOutTest. */
class BookVaultSignOutTest {

    private val events = mutableListOf<String>()

    private fun signOut(syncProgress: suspend () -> Unit = { events += "send places" }) = bookVaultSignOut(
        session = { events += "end session" },
        logout = { events += "logout" },
        syncProgress = syncProgress,
        forgetAccount = { events += "forget account" },
        forgetPlaces = { events += "forget places" },
    )

    @Test
    fun `sends the places read here while still signed in, then signs out`() = runTest {
        signOut()()

        events shouldBe listOf("send places", "end session", "forget account", "forget places", "logout")
    }

    @Test
    fun `a server too slow to take them doesn't keep the reader signed in`() = runTest {
        signOut(syncProgress = { awaitCancellation() })()

        events shouldBe listOf("end session", "forget account", "forget places", "logout")
    }
}
