package org.jellyfin.mobile

import androidx.fragment.app.Fragment
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.common.setup.LoginFragment
import org.jellyfin.mobile.setup.ConnectFragment
import org.junit.Test

/**
 * Tests [shouldNavigateToLibrary]: rotating the device on a non-Library screen must not send the
 * user back to the Library. [MainActivity] re-subscribes to `serverState` on every recreation and
 * gets the cached `ServerState.Available` replayed, so the decision must tell a replay apart from
 * first sign-in.
 *
 * Plain JUnit: only the fragments' types matter, so they're constructed without a lifecycle.
 * BookVault's `BookMainActivityTest` covers the full `recreate()` path for the same logic.
 */
class MainActivityTest {

    @Test
    fun `shouldNavigateToLibrary is true with no fragment restored yet`() {
        shouldNavigateToLibrary(null) shouldBe true
    }

    @Test
    fun `shouldNavigateToLibrary is true while still on the login screen`() {
        shouldNavigateToLibrary(LoginFragment()) shouldBe true
    }

    @Test
    fun `shouldNavigateToLibrary is true while still on the connect screen`() {
        shouldNavigateToLibrary(ConnectFragment()) shouldBe true
    }

    @Test
    fun `shouldNavigateToLibrary is false for any other already-signed-in screen`() {
        shouldNavigateToLibrary(Fragment()) shouldBe false
    }
}
