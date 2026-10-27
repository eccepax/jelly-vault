package org.jellyfin.mobile.music.account

import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.server.ServerSession
import org.jellyfin.mobile.music.catalog.FavoritesRepository
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.music.playback.PlaybackReportingTracker

/**
 * TuneVault's sign-out: stops playback and sends its last report while the token still works, and
 * forgets unsaved favorite changes along with [clearAccountData] (sends what's still owed to the
 * server, then forgets downloads and plays made offline).
 */
fun tuneVaultSignOut(
    playback: PlaybackController,
    tracker: PlaybackReportingTracker,
    favorites: FavoritesRepository,
    session: ServerSession,
    clearAccountData: suspend () -> Unit,
    logout: suspend () -> Unit,
    /** Ends a cast, once playback has stopped: the cast device holds stream urls with the token in them. */
    stopCasting: () -> Unit,
) = SignOut(
    session = session,
    logout = logout,
    whileSignedIn = {
        playback.stop()
        stopCasting()
        tracker.finish()
    },
    forgetAccount = {
        favorites.clear()
        clearAccountData()
    },
)
