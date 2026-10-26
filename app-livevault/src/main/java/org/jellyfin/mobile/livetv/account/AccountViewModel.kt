package org.jellyfin.mobile.livetv.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jellyfin.mobile.common.account.SigningOut

/** Signing out of the signed-in app. */
class AccountViewModel(signOut: suspend () -> Unit) : ViewModel() {

    private val signingOutNow = SigningOut(viewModelScope, signOut)

    val signingOut: StateFlow<Boolean> = signingOutNow.inProgress

    /** Becomes true once signed out; the app then starts over at sign-in. */
    val signedOut: StateFlow<Boolean> = signingOutNow.done

    /** Each sign out that failed, to say so; signing out can be tried again. */
    val signOutFailures: Flow<Unit> = signingOutNow.failures

    fun signOut() = signingOutNow.start()
}
