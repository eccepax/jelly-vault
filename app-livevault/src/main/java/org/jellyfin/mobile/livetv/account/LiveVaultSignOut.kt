package org.jellyfin.mobile.livetv.account

import org.jellyfin.mobile.cast.stopPlaybackThenForgetCast
import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.server.ServerSession
import org.jellyfin.mobile.livetv.playback.StreamCloser
import org.jellyfin.mobile.livetv.viewing.ViewingMemory

/**
 * LiveVault's sign-out: closes every open live stream while the token still works (one left open
 * would hold the provider's single login), then ends any cast and forgets it, even if closing failed
 * (see stopPlaybackThenForgetCast); the channel the cast was playing is then over, not opened here
 * again (see LiveCasting), and its stream, closed already, isn't closed twice (see StreamCloser).
 * Forgets streams that couldn't be closed, along with what was being watched.
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
    whileSignedIn = { stopPlaybackThenForgetCast(stopPlayback = closeAll, forgetCast = forgetCast) },
    forgetAccount = {
        closer.forgetAll()
        memory.forget()
    },
)
