package org.jellyfin.mobile.music.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.mobile.music.catalog.FavoritesRepository
import org.jellyfin.mobile.music.catalog.ServerSession
import org.jellyfin.mobile.music.playback.PlaybackController
import org.jellyfin.mobile.music.playback.PlaybackReportingTracker
import timber.log.Timber

/**
 * Signs out: stops playback and sends its last report while the token still works, revokes the
 * session on the server (best effort: an unreachable server never blocks signing out), forgets this
 * account's state, then [logout] forgets the user locally.
 */
class SignOut(
    private val playback: PlaybackController,
    private val tracker: PlaybackReportingTracker,
    private val favorites: FavoritesRepository,
    private val session: ServerSession,
    /**
     * Sends what's still owed to the server, then forgets everything kept for this account
     * (downloads, plays made offline): the next one to sign in mustn't find them.
     */
    private val clearAccountData: suspend () -> Unit,
    private val logout: suspend () -> Unit,
) {
    suspend operator fun invoke() {
        playback.stop()
        tracker.finish()
        try {
            withTimeoutOrNull(END_SESSION_TIMEOUT_MS) { session.end() }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Timber.w(e, "Couldn't end the session on the server")
        }
        favorites.clear()
        clearAccountData()
        logout()
    }

    private companion object {
        const val END_SESSION_TIMEOUT_MS = 5_000L
    }
}
