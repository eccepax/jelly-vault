package org.jellyfin.mobile.books.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.app.EpubFontFamily
import org.jellyfin.mobile.common.downloads.DownloadSettings
import org.jellyfin.sdk.model.ClientInfo

/** BookVault's settings: each change is saved, then shown by reading the settings back. */
class BookSettingsViewModel(
    private val preferences: BookAppPreferences,
    private val downloads: DownloadSettings,
    private val signOut: suspend () -> Unit,
    private val app: ClientInfo,
) : ViewModel() {

    private val saved = MutableStateFlow(read())

    /** The settings, with the space downloads take as it changes. */
    val state: StateFlow<BookSettingsState> =
        combine(saved, downloads.bytesUsed, downloads.hasDownloads) { settings, bytes, hasDownloads ->
            settings.copy(downloadBytes = bytes, hasDownloads = hasDownloads)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, saved.value)

    private val _signedOut = MutableStateFlow(false)

    /** Becomes true once signed out; the app then starts over at sign-in. */
    val signedOut: StateFlow<Boolean> = _signedOut.asStateFlow()

    private var signingOut = false

    /**
     * Reads the settings again, for the screen as it opens: this view model outlives it, and the
     * reading settings can change in the reader meanwhile.
     */
    fun reload() {
        saved.value = read()
    }

    fun setFontScale(scale: Float) = save { preferences.epubFontScale = scale }

    fun setFontFamily(family: EpubFontFamily) = save { preferences.epubFontFamily = family }

    fun setLineSpacing(spacing: Float) = save { preferences.epubLineSpacing = spacing }

    fun setRemoveDownloadsOnSignOut(remove: Boolean) = save { preferences.removeDownloadsOnSignOut = remove }

    /** Saved, and applied to downloads already waiting too, by the download settings. */
    fun setWifiOnly(wifiOnly: Boolean) = save { downloads.wifiOnly = wifiOnly }

    fun removeAllDownloads() {
        viewModelScope.launch { downloads.removeAll() }
    }

    fun signOut() {
        if (signingOut) return
        signingOut = true
        viewModelScope.launch {
            signOut.invoke()
            _signedOut.value = true
        }
    }

    private fun save(change: () -> Unit) {
        change()
        saved.value = read()
    }

    private fun read() = BookSettingsState(
        fontScale = preferences.epubFontScale,
        fontFamily = preferences.epubFontFamily,
        lineSpacing = preferences.epubLineSpacing,
        removeDownloadsOnSignOut = preferences.removeDownloadsOnSignOut,
        downloadOnWifiOnly = downloads.wifiOnly,
        appName = app.name,
        appVersion = app.version,
    )
}
