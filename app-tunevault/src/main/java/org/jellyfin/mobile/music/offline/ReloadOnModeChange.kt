package org.jellyfin.mobile.music.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Reloads this screen's data whenever the app switches between the server's library and the
 * downloads, so nothing keeps showing the other one: a list loaded offline would otherwise stay
 * downloads-only after the Vault came back, and one loaded online would list songs that can't play.
 * Screens off-screen reload too, since their ViewModels outlive them.
 */
fun <VM : ViewModel> VM.reloadingOnModeChange(isOffline: Flow<Boolean>, reload: VM.() -> Unit): VM {
    viewModelScope.launch { isOffline.distinctUntilChanged().drop(1).collect { reload() } }
    return this
}
