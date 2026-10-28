package org.jellyfin.mobile.settings

import org.jellyfin.mobile.cast.stopPlaybackThenForgetCast
import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.server.ServerSession

/**
 * AudioVault's sign-out: stops playback, sending its last report while the token still works (where
 * the listener got to is read from the player, so this goes before anything empties it), then ends
 * any cast and forgets it, even if stopping failed (see stopPlaybackThenForgetCast).
 * [forgetDownloads] removes downloads if the user asked for that in Settings.
 */
fun audioVaultSignOut(
    session: ServerSession,
    logout: suspend () -> Unit,
    stopPlayback: suspend () -> Unit,
    forgetCast: () -> Unit,
    forgetDownloads: suspend () -> Unit,
) = SignOut(
    session = session,
    logout = logout,
    whileSignedIn = { stopPlaybackThenForgetCast(stopPlayback, forgetCast) },
    forgetAccount = forgetDownloads,
)
