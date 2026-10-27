package org.jellyfin.mobile.music.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.mobile.common.account.SigningOut
import org.jellyfin.mobile.common.downloads.DownloadSettings
import org.jellyfin.mobile.common.downloads.DownloadSpace
import org.jellyfin.mobile.common.downloads.DownloadsInSettings
import org.jellyfin.mobile.music.app.PlaybackPreferences
import org.jellyfin.mobile.music.catalog.Account
import org.jellyfin.mobile.music.catalog.AccountInfo
import org.jellyfin.mobile.music.domain.StreamingQuality
import org.jellyfin.mobile.music.playback.SetVolumeLevelling
import org.jellyfin.mobile.music.playback.ShowSleepTimer
import org.jellyfin.sdk.model.ClientInfo
import timber.log.Timber

data class SettingsUiState(
    /** Null until loaded, or if it can't be; signing out works either way. */
    val account: Account? = null,
    val streamingQuality: StreamingQuality = StreamingQuality.ORIGINAL,
    /** The app's name as the bug form lists it (not app_name, which debug builds change). */
    val appName: String = "",
    val appVersion: String = "",
    val signingOut: Boolean = false,
    val downloadQuality: StreamingQuality = StreamingQuality.ORIGINAL,
    val downloadOnWifiOnly: Boolean = true,
    val removeDownloadsOnSignOut: Boolean = false,
    /** The space downloads take; null until known, and then nothing is said about it. */
    val downloadSpace: DownloadSpace? = null,
    val showSleepTimer: Boolean = true,
    val volumeLevelling: Boolean = true,
)

class SettingsViewModel(
    private val accountInfo: AccountInfo,
    private val preferences: PlaybackPreferences,
    private val downloads: DownloadSettings,
    private val sleepTimer: ShowSleepTimer,
    private val levelling: SetVolumeLevelling,
    signOut: suspend () -> Unit,
    app: ClientInfo,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            streamingQuality = preferences.streamingQuality,
            appName = app.name,
            appVersion = app.version,
            downloadQuality = preferences.downloadQuality,
            downloadOnWifiOnly = downloads.wifiOnly,
            removeDownloadsOnSignOut = preferences.removeDownloadsOnSignOut,
            showSleepTimer = preferences.showSleepTimer,
            volumeLevelling = preferences.volumeLevelling,
        ),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val signingOut = SigningOut(viewModelScope, signOut)

    /** Becomes true once signed out; the app then starts over at sign-in. */
    val signedOut: StateFlow<Boolean> = signingOut.done

    /** Each sign out that failed, to say so; signing out can be tried again. */
    val signOutFailures: Flow<Unit> = signingOut.failures

    private val downloadsInSettings = DownloadsInSettings(viewModelScope, downloads)

    /** The space downloads take, while the screen shows it; null until known. */
    val downloadSpace: StateFlow<DownloadSpace?> = downloadsInSettings.space

    /** Each time removing all downloads failed, to say so. */
    val removeDownloadsFailures: Flow<Unit> = downloadsInSettings.removeFailures

    init {
        viewModelScope.launch {
            signingOut.inProgress.collect { busy -> _state.update { it.copy(signingOut = busy) } }
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
        downloads.wifiOnly = wifiOnly
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

    fun removeAllDownloads() = downloadsInSettings.removeAll()

    fun signOut() = signingOut.start()
}
