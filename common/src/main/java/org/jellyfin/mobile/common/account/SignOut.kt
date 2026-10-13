package org.jellyfin.mobile.common.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.mobile.common.server.ServerSession
import timber.log.Timber

/**
 * Signs out: finishes [whileSignedIn] work while the token still works, revokes the session on the
 * server (best effort: an unreachable server never blocks signing out), forgets this account's
 * state ([forgetAccount]: the next one to sign in mustn't find it), then [logout] forgets the user
 * locally.
 */
class SignOut(
    private val session: ServerSession,
    private val logout: suspend () -> Unit,
    /** Work that still needs the token, such as stopping playback and sending its last report. */
    private val whileSignedIn: suspend () -> Unit = {},
    private val forgetAccount: suspend () -> Unit = {},
) {
    suspend operator fun invoke() {
        whileSignedIn()
        try {
            withTimeoutOrNull(END_SESSION_TIMEOUT_MS) { session.end() }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Timber.w(e, "Couldn't end the session on the server")
        }
        forgetAccount()
        logout()
    }

    private companion object {
        const val END_SESSION_TIMEOUT_MS = 5_000L
    }
}
