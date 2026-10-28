package org.jellyfin.mobile.livetv.account

import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.server.ServerSession
import org.jellyfin.mobile.livetv.playback.StreamCloser
import org.jellyfin.mobile.livetv.viewing.ViewingMemory

/**
 * LiveVault's sign-out: closes every open live stream while the token still works (one left open
 * would hold the provider's single login), then ends any cast and forgets it (see
 * Casting.forgetCast), even if closing failed, since the stream url on the cast device carries the
 * token; and forgets streams that couldn't be closed, along with what was being watched.
 */
fun liveVaultSignOut(
    closer: StreamCloser,
    memory: ViewingMemory,
    session: ServerSession,
    logout: suspend () -> Unit,
    forgetCast: () -> Unit,
    closeAll: suspend () -> Unit = closer::closeAll,
) = SignOut(
    session = session,
    logout = logout,
    whileSignedIn = {
        try {
            closeAll()
        } finally {
            forgetCast()
        }
    },
    forgetAccount = {
        closer.forgetAll()
        memory.forget()
    },
)
