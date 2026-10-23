package org.jellyfin.mobile.e2e

import org.junit.Test

/** Signing in without typing a password: approve the code shown on screen from another device. */
@AccountArea
class QuickConnectE2ETest : LoggedInE2ETest() {

    @Test
    fun approvingTheCodeFromAnotherDeviceSignsIn() {
        connectToVault()
        val code = startQuickConnect()

        approveQuickConnect(code)

        waitForLibraryItems(timeoutMillis = 30_000L)
    }
}
