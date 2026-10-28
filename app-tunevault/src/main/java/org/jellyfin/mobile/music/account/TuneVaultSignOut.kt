package org.jellyfin.mobile.music.account

import org.jellyfin.mobile.cast.stopPlaybackThenForgetCast
import org.jellyfin.mobile.common.account.SignOut
import org.jellyfin.mobile.common.server.ServerSession
import org.jellyfin.mobile.music.catalog.FavoritesRepository
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.music.playback.PlaybackReportingTracker

/**
 * TuneVault's sign-out: stops playback and sends its last report while the token still works, then
 * ends any cast and forgets it (see stopPlaybackThenForgetCast), and forgets unsaved favorite changes along with [clearAccountData] (sends what's still owed to the
 * server, then forgets downloads and plays made offline).
 */
fun tuneVaultSignOut(
    playback: PlaybackController,
    tracker: PlaybackReportingTracker,
    favorites: FavoritesRepository,
    session: ServerSession,
    clearAccountData: suspend () -> Unit,
    logout: suspend () -> Unit,
    /** Ends a cast and forgets its queue (see Casting.forgetCast): the cast device holds stream urls with the token in them. */
    forgetCast: () -> Unit,
) = SignOut(
    session = session,
    logout = logout,
    whileSignedIn = {
        stopPlaybackThenForgetCast(
            stopPlayback = {
                playback.stop()
                tracker.finish()
            },
            forgetCast = forgetCast,
        )
    },
    forgetAccount = {
        favorites.clear()
        clearAccountData()
    },
)
