package org.jellyfin.mobile.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.DownloadManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.account.SigningOut
import org.jellyfin.mobile.downloads.applyDownloadMethod
import org.jellyfin.mobile.player.playback.AudiobookPlaybackController
import org.jellyfin.sdk.model.ClientInfo

/** AudioVault's settings: each change is saved, then shown by reading the settings back. */
class SettingsViewModel(
    private val preferences: AudioVaultAppPreferences,
    private val downloadManager: DownloadManager,
    private val playback: AudiobookPlaybackController,
    signOut: suspend () -> Unit,
    private val app: ClientInfo,
) : ViewModel() {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val signingOut = SigningOut(viewModelScope, signOut)

    /** Becomes true once signed out; the app then starts over at sign-in. */
    val signedOut: StateFlow<Boolean> = signingOut.done

    /** Each sign out that failed, to say so; signing out can be tried again. */
    val signOutFailures: Flow<Unit> = signingOut.failures

    /**
     * Reads the settings again, for the screen as it opens: this view model outlives it, and a
     * setting can change elsewhere meanwhile (the networks downloads may use, asked by the library).
     */
    fun reload() {
        _state.value = read()
    }

    fun setHoldToSpeedUp(enabled: Boolean) = save { preferences.exoPlayerAllowPressSpeedUp = enabled }

    fun setSkipSilence(enabled: Boolean) = save {
        preferences.skipSilenceEnabled = enabled
        // The book playing, too: the service reads the setting only as it starts
        playback.setSkipSilence(enabled)
    }

    fun setFlatLibrary(flat: Boolean) = save { preferences.libraryGlobalFlattened = flat }

    fun setDownloadMethod(method: Int) = save {
        preferences.downloadMethod = method
        // Applies to downloads already waiting too, not just new ones
        downloadManager.applyDownloadMethod(method)
    }

    fun setRemoveDownloadsOnSignOut(remove: Boolean) = save { preferences.removeDownloadsOnSignOut = remove }

    fun signOut() = signingOut.start()

    private fun save(change: () -> Unit) {
        change()
        _state.value = read()
    }

    private fun read() = SettingsUiState(
        holdToSpeedUp = preferences.exoPlayerAllowPressSpeedUp,
        skipSilence = preferences.skipSilenceEnabled,
        flatLibrary = preferences.libraryGlobalFlattened,
        downloadMethod = preferences.downloadMethod,
        removeDownloadsOnSignOut = preferences.removeDownloadsOnSignOut,
        appName = app.name,
        appVersion = app.version,
    )
}
