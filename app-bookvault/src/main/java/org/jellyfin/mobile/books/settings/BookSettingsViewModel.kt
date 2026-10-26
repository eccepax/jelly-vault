package org.jellyfin.mobile.books.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.app.EpubFontFamily
import org.jellyfin.mobile.common.account.SigningOut
import org.jellyfin.mobile.common.downloads.DownloadSettings
import org.jellyfin.mobile.common.downloads.DownloadSpace
import org.jellyfin.mobile.common.downloads.DownloadsInSettings
import org.jellyfin.sdk.model.ClientInfo

/** BookVault's settings: each change is saved, then shown by reading the settings back. */
class BookSettingsViewModel(
    private val preferences: BookAppPreferences,
    private val downloads: DownloadSettings,
    signOut: suspend () -> Unit,
    private val app: ClientInfo,
) : ViewModel() {

    private val saved = MutableStateFlow(read())
    val state: StateFlow<BookSettingsState> = saved.asStateFlow()

    private val downloadsInSettings = DownloadsInSettings(viewModelScope, downloads)

    /** The space downloads take, while the screen shows it; null until known. */
    val downloadSpace: StateFlow<DownloadSpace?> = downloadsInSettings.space

    /** Each time removing all downloads failed, to say so. */
    val removeDownloadsFailures: Flow<Unit> = downloadsInSettings.removeFailures

    private val signingOut = SigningOut(viewModelScope, signOut)

    /** Becomes true once signed out; the app then starts over at sign-in. */
    val signedOut: StateFlow<Boolean> = signingOut.done

    /** Each sign out that failed, to say so; signing out can be tried again. */
    val signOutFailures: Flow<Unit> = signingOut.failures

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

    fun removeAllDownloads() = downloadsInSettings.removeAll()

    fun signOut() = signingOut.start()

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
