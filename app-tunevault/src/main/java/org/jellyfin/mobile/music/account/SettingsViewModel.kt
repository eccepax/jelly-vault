package org.jellyfin.mobile.music.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.mobile.music.app.PlaybackPreferences
import org.jellyfin.mobile.music.catalog.Account
import org.jellyfin.mobile.music.catalog.AccountInfo
import org.jellyfin.mobile.music.domain.StreamingQuality
import org.jellyfin.mobile.music.downloads.DownloadSettings
import org.jellyfin.mobile.music.playback.SetVolumeLevelling
import org.jellyfin.mobile.music.playback.ShowSleepTimer
import timber.log.Timber

data class SettingsUiState(
    /** Null until loaded, or if it can't be; signing out works either way. */
    val account: Account? = null,
    val streamingQuality: StreamingQuality = StreamingQuality.ORIGINAL,
    val appVersion: String = "",
    val signingOut: Boolean = false,
    val downloadQuality: StreamingQuality = StreamingQuality.ORIGINAL,
    val downloadOnWifiOnly: Boolean = true,
    val removeDownloadsOnSignOut: Boolean = false,
    val downloadBytes: Long = 0L,
    val showSleepTimer: Boolean = true,
    val volumeLevelling: Boolean = true,
)

class SettingsViewModel(
    private val accountInfo: AccountInfo,
    private val preferences: PlaybackPreferences,
    private val downloads: DownloadSettings,
    private val sleepTimer: ShowSleepTimer,
    private val levelling: SetVolumeLevelling,
    private val signOut: suspend () -> Unit,
    appVersion: String,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            streamingQuality = preferences.streamingQuality,
            appVersion = appVersion,
            downloadQuality = preferences.downloadQuality,
            downloadOnWifiOnly = preferences.downloadOnWifiOnly,
            removeDownloadsOnSignOut = preferences.removeDownloadsOnSignOut,
            showSleepTimer = preferences.showSleepTimer,
            volumeLevelling = preferences.volumeLevelling,
        ),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _signedOut = MutableStateFlow(false)

    /** Becomes true once signed out; the app then starts over at sign-in. */
    val signedOut: StateFlow<Boolean> = _signedOut.asStateFlow()

    init {
        viewModelScope.launch {
            downloads.bytesUsed.collect { bytes ->
                _state.update {
                    it.copy(
                        downloadBytes = bytes,
                    )
                }
            }
        }
        viewModelScope.launch {
            try {
                val account = accountInfo.current()
                _state.update { it.copy(account = account) }
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Timber.w(e, "Couldn't load the account")
            }
        }
    }

    fun setStreamingQuality(quality: StreamingQuality) {
        preferences.streamingQuality = quality
        _state.update { it.copy(streamingQuality = quality) }
    }

    fun setDownloadQuality(quality: StreamingQuality) {
        preferences.downloadQuality = quality
        _state.update { it.copy(downloadQuality = quality) }
    }

    fun setRemoveDownloadsOnSignOut(remove: Boolean) {
        preferences.removeDownloadsOnSignOut = remove
        _state.update { it.copy(removeDownloadsOnSignOut = remove) }
    }

    fun setDownloadOnWifiOnly(wifiOnly: Boolean) {
        preferences.downloadOnWifiOnly = wifiOnly
        downloads.applyWifiOnly(wifiOnly)
        _state.update { it.copy(downloadOnWifiOnly = wifiOnly) }
    }

    fun setShowSleepTimer(shown: Boolean) {
        sleepTimer.setShown(shown)
        _state.update { it.copy(showSleepTimer = shown) }
    }

    fun setVolumeLevelling(on: Boolean) {
        levelling.setOn(on)
        _state.update { it.copy(volumeLevelling = on) }
    }

    fun removeAllDownloads() {
        viewModelScope.launch { downloads.removeAll() }
    }

    fun signOut() {
        if (_state.value.signingOut) return
        _state.update { it.copy(signingOut = true) }
        viewModelScope.launch {
            signOut.invoke()
            _signedOut.value = true
        }
    }
}
