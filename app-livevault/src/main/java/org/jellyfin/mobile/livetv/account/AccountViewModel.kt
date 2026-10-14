package org.jellyfin.mobile.livetv.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Signing out of the signed-in app. */
class AccountViewModel(private val signOut: suspend () -> Unit) : ViewModel() {

    private val _signingOut = MutableStateFlow(false)
    val signingOut: StateFlow<Boolean> = _signingOut.asStateFlow()

    private val _signedOut = MutableStateFlow(false)

    /** Becomes true once signed out; the app then starts over at sign-in. */
    val signedOut: StateFlow<Boolean> = _signedOut.asStateFlow()

    fun signOut() {
        if (_signingOut.value) return
        _signingOut.value = true
        viewModelScope.launch {
            signOut.invoke()
            _signedOut.value = true
        }
    }
}
