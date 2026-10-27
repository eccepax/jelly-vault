package org.jellyfin.mobile.settings

import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.server.ServerSession

/**
 * AudioVault's sign-out: stops playback (and sends its last report) while the token still works,
 * then ends any cast, since the cast device holds stream urls with the token in them.
 * [forgetDownloads] removes them if the user asked for that in Settings.
 */
fun audioVaultSignOut(
    session: ServerSession,
    logout: suspend () -> Unit,
    stopPlayback: suspend () -> Unit,
    stopCasting: () -> Unit,
    forgetDownloads: suspend () -> Unit,
) = SignOut(
    session = session,
    logout = logout,
    whileSignedIn = {
        stopPlayback()
        stopCasting()
    },
    forgetAccount = forgetDownloads,
)
