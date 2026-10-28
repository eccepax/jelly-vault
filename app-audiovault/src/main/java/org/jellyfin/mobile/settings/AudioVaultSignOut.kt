package org.jellyfin.mobile.settings

import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.server.ServerSession

/**
 * AudioVault's sign-out: stops playback, sending its last report while the token still works (where
 * the listener got to is read from the player, so this goes before anything empties it), then ends
 * any cast and forgets it (see Casting.forgetCast), even if stopping failed, since the cast device
 * holds stream urls with the token in them. [forgetDownloads] removes downloads if the user asked
 * for that in Settings.
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
    whileSignedIn = {
        try {
            stopPlayback()
        } finally {
            forgetCast()
        }
    },
    forgetAccount = forgetDownloads,
)
