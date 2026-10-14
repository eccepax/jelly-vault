package org.jellyfin.mobile.common.setup.quickconnect

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

/**
 * Jellyfin's Quick Connect: this device shows a code, the user approves it from a device that's
 * already signed in (another app, or the web client's profile page), and this device signs in
 * without anyone typing a password — which on a TV means a remote and an on-screen keyboard.
 */
interface QuickConnect {
    suspend fun isEnabled(): Boolean
    suspend fun request(): QuickConnectRequest
    suspend fun isApproved(secret: String): Boolean
    suspend fun signIn(secret: String): SignedInUser
}

/** [code] is shown to the user; [secret] identifies the request to the server. */
data class QuickConnectRequest(val code: String, val secret: String)

data class SignedInUser(val userId: String, val accessToken: String)

sealed interface QuickConnectState {
    data object Unavailable : QuickConnectState
    data class WaitingForApproval(val code: String) : QuickConnectState
    data class SignedIn(val user: SignedInUser) : QuickConnectState
    data object Expired : QuickConnectState
    data object Failed : QuickConnectState
}

/**
 * Requests a code and waits for it to be approved, checking every [pollIntervalMs]; gives up after
 * [expiresAfterMs] (the server forgets codes after a few minutes). Completes after its last state.
 */
fun quickConnectSignIn(
    server: QuickConnect,
    pollIntervalMs: Long = POLL_INTERVAL_MS,
    expiresAfterMs: Long = EXPIRES_AFTER_MS,
): Flow<QuickConnectState> = flow {
    val state = try {
        if (!server.isEnabled()) {
            QuickConnectState.Unavailable
        } else {
            val request = server.request()
            emit(QuickConnectState.WaitingForApproval(request.code))
            var waited = 0L
            var approved = false
            while (!approved && waited < expiresAfterMs) {
                delay(pollIntervalMs)
                waited += pollIntervalMs
                approved = server.isApproved(request.secret)
            }
            if (approved) QuickConnectState.SignedIn(server.signIn(request.secret)) else QuickConnectState.Expired
        }
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Timber.w(e, "Quick Connect failed")
        QuickConnectState.Failed
    }
    emit(state)
}

private const val POLL_INTERVAL_MS = 5_000L
private const val EXPIRES_AFTER_MS = 10 * 60_000L
