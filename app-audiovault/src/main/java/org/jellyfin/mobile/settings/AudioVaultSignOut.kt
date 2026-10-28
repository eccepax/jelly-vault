package org.jellyfin.mobile.settings

import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.server.ServerSession

/**
 * AudioVault's sign-out: ends any cast and forgets it first (see Casting.forgetCast), since the cast
 * device holds stream urls with the token in them and nothing after should keep it going, then
 * stops playback (and sends its last report) while the token still works. [forgetDownloads] removes
 * downloads if the user asked for that in Settings.
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
        forgetCast()
        stopPlayback()
    },
    forgetAccount = forgetDownloads,
)
