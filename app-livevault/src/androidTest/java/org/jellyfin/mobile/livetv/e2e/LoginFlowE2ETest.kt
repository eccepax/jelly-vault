package org.jellyfin.mobile.livetv.e2e

import org.jellyfin.mobile.livetv.LiveMainActivity
import org.jellyfin.mobile.testing.endtoend.SignedInE2ETest
import org.junit.Test

class LoginFlowE2ETest : SignedInE2ETest<LiveMainActivity>(LiveMainActivity::class.java) {

    override fun clearAppState() {
        clearLiveVaultState()
    }

    @Test
    fun signingInOpensLiveTv() {
        connectAndSignIn()
        waitForComposeText("Live TV")
    }
}
