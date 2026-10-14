package org.jellyfin.mobile.livetv.e2e

import org.jellyfin.mobile.livetv.LiveMainActivity
import org.jellyfin.mobile.livetv.data.LiveDatabase
import org.jellyfin.mobile.testing.endtoend.SignedInE2ETest
import org.junit.Test

/** Signing in on a TV without typing a password: approve the code shown on screen from another device. */
class QuickConnectE2ETest : SignedInE2ETest<LiveMainActivity>(LiveMainActivity::class.java) {

    override fun clearAppState() {
        koinGet<LiveDatabase>().clearAllTables()
    }

    @Test
    fun approvingTheCodeFromAnotherDeviceSignsIn() {
        connectToVault()
        val code = startQuickConnect()

        approveQuickConnect(code)

        waitForComposeText("Live TV", timeoutMillis = 30_000L)
    }
}
