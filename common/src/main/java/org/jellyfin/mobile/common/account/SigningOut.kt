package org.jellyfin.mobile.common.account

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Signing out from a settings screen, in [scope] (the screen's view model's): one sign out at a
 * time, and one that fails ([failures], shown with
 * [org.jellyfin.mobile.common.ui.components.SignOutFailures]) can be tried again.
 */
class SigningOut(private val scope: CoroutineScope, private val signOut: suspend () -> Unit) {

    private val _inProgress = MutableStateFlow(false)
    val inProgress: StateFlow<Boolean> = _inProgress.asStateFlow()

    private val _done = MutableStateFlow(false)

    /** Becomes true once signed out; the app then starts over at sign-in. */
    val done: StateFlow<Boolean> = _done.asStateFlow()

    private val _failures = Channel<Unit>(Channel.CONFLATED)

    /** Each sign out that failed, to say so. */
    val failures: Flow<Unit> = _failures.receiveAsFlow()

    /** Signs out, unless already signing out. */
    fun start() {
        if (_inProgress.value) return
        _inProgress.value = true
        scope.launch {
            try {
                signOut()
                _done.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.e(e, "Couldn't sign out")
                _inProgress.value = false
                _failures.send(Unit)
            }
        }
    }
}
