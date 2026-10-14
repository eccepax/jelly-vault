package org.jellyfin.mobile.livetv.account

import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.server.ServerSession
import org.jellyfin.mobile.livetv.playback.StreamCloser

/**
 * LiveVault's sign-out: closes every open live stream while the token still works (one left open
 * would hold the provider's single login), and forgets any that couldn't be closed.
 */
fun liveVaultSignOut(
    closer: StreamCloser,
    session: ServerSession,
    logout: suspend () -> Unit,
) = SignOut(
    session = session,
    logout = logout,
    whileSignedIn = { closer.closeAll() },
    forgetAccount = { closer.forgetAll() },
)
