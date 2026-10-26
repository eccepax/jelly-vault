package org.jellyfin.mobile.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jellyfin.mobile.app.AudioVaultAppPreferences
import org.jellyfin.mobile.common.account.SigningOut
import org.jellyfin.mobile.common.downloads.DownloadSettings
import org.jellyfin.mobile.player.playback.AudiobookPlaybackController
import org.jellyfin.sdk.model.ClientInfo

/** AudioVault's settings: each change is saved, then shown by reading the settings back. */
class SettingsViewModel(
    private val preferences: AudioVaultAppPreferences,
    private val downloads: DownloadSettings,
    private val playback: AudiobookPlaybackController,
    signOut: suspend () -> Unit,
    private val app: ClientInfo,
) : ViewModel() {

    private val saved = MutableStateFlow(read())

    /** The settings, with the space downloads take as it changes. */
    val state: StateFlow<SettingsUiState> =
        combine(saved, downloads.bytesUsed, downloads.hasDownloads) { settings, bytes, hasDownloads ->
            settings.copy(downloadBytes = bytes, hasDownloads = hasDownloads)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, saved.value)

    private val signingOut = SigningOut(viewModelScope, signOut)

    /** Becomes true once signed out; the app then starts over at sign-in. */
    val signedOut: StateFlow<Boolean> = signingOut.done

    /** Each sign out that failed, to say so; signing out can be tried again. */
    val signOutFailures: Flow<Unit> = signingOut.failures

    /**
     * Reads the settings again, for the screen as it opens: this view model outlives it, and a
     * setting can change elsewhere meanwhile.
     */
    fun reload() {
        saved.value = read()
    }

    fun setHoldToSpeedUp(enabled: Boolean) = save { preferences.exoPlayerAllowPressSpeedUp = enabled }

    fun setSkipSilence(enabled: Boolean) = save {
        preferences.skipSilenceEnabled = enabled
        // The book playing, too: the service reads the setting only as it starts
        playback.setSkipSilence(enabled)
    }

    fun setFlatLibrary(flat: Boolean) = save { preferences.libraryGlobalFlattened = flat }

    /** Saved, and applied to downloads already waiting too, by the download settings. */
    fun setWifiOnly(wifiOnly: Boolean) = save { downloads.wifiOnly = wifiOnly }

    fun removeAllDownloads() {
        viewModelScope.launch { downloads.removeAll() }
    }

    fun setRemoveDownloadsOnSignOut(remove: Boolean) = save { preferences.removeDownloadsOnSignOut = remove }

    fun signOut() = signingOut.start()

    private fun save(change: () -> Unit) {
        change()
        saved.value = read()
    }

    private fun read() = SettingsUiState(
        holdToSpeedUp = preferences.exoPlayerAllowPressSpeedUp,
        skipSilence = preferences.skipSilenceEnabled,
        flatLibrary = preferences.libraryGlobalFlattened,
        downloadOnWifiOnly = downloads.wifiOnly,
        removeDownloadsOnSignOut = preferences.removeDownloadsOnSignOut,
        appName = app.name,
        appVersion = app.version,
    )
}
