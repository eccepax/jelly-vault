package org.jellyfin.mobile.books.app

import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.server.ServerSession

private const val SYNC_TIMEOUT_MS = 5_000L

/**
 * Signs out of BookVault: first sends the server the places read here, while the token still
 * works, so they aren't lost with the account (an unreachable server only delays it a little).
 * Then forgets them, so the next account to sign in neither opens its books at them nor sends them
 * as its own.
 */
fun bookVaultSignOut(
    session: ServerSession,
    logout: suspend () -> Unit,
    syncProgress: suspend () -> Unit,
    forgetAccount: suspend () -> Unit,
    forgetPlaces: suspend () -> Unit,
) = SignOut(
    session,
    logout,
    whileSignedIn = { withTimeoutOrNull(SYNC_TIMEOUT_MS) { syncProgress() } },
    forgetAccount = {
        forgetAccount()
        forgetPlaces()
    },
)
