package org.jellyfin.mobile.common.account

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.common.server.ServerSession
import org.junit.jupiter.api.Test
import java.io.IOException

class SignOutTest {

    private val events = mutableListOf<String>()

    private fun signOut(session: ServerSession = ServerSession { events += "end session" }) = SignOut(
        session = session,
        logout = { events += "logout" },
        whileSignedIn = { events += "finish work that needs the token" },
        forgetAccount = { events += "forget account data" },
    )

    @Test
    fun `finishes work that needs the token, ends the session, forgets the account, then logs out`() = runTest {
        signOut()()

        events shouldBe listOf("finish work that needs the token", "end session", "forget account data", "logout")
    }

    @Test
    fun `still logs out when the server can't end the session`() = runTest {
        signOut(ServerSession { throw IOException("offline") })()

        events shouldBe listOf("finish work that needs the token", "forget account data", "logout")
    }

    @Test
    fun `still logs out, after a while, when the server never answers`() = runTest {
        signOut(ServerSession { awaitCancellation() })()

        events shouldBe listOf("finish work that needs the token", "forget account data", "logout")
    }

    @Test
    fun `being cancelled stops the sign-out rather than being mistaken for a server failure`() = runTest {
        shouldThrow<CancellationException> {
            signOut(ServerSession { throw CancellationException("left the screen") })()
        }

        events shouldBe listOf("finish work that needs the token")
    }

    @Test
    fun `the extra steps are optional`() = runTest {
        SignOut(session = { events += "end session" }, logout = { events += "logout" })()

        events shouldBe listOf("end session", "logout")
    }
}
