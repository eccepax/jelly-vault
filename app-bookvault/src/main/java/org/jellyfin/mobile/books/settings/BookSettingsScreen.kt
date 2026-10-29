package org.jellyfin.mobile.books.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.app.EpubFontFamily
import org.jellyfin.mobile.books.reader.EpubTextSettings
import org.jellyfin.mobile.books.reader.TextSetting
import org.jellyfin.mobile.books.reader.TextSettingActions
import org.jellyfin.mobile.books.reader.TextSettingPicker
import org.jellyfin.mobile.books.reader.TextSettingRows
import org.jellyfin.mobile.common.downloads.DownloadSpace
import org.jellyfin.mobile.common.ui.components.AboutSettings
import org.jellyfin.mobile.common.ui.components.DownloadsSpaceSetting
import org.jellyfin.mobile.common.ui.components.RemoveDownloadsFailures
import org.jellyfin.mobile.common.ui.components.SettingsSectionHeader
import org.jellyfin.mobile.common.ui.components.SettingsSwitchRow
import org.jellyfin.mobile.common.ui.components.SignOutFailures
import org.jellyfin.mobile.common.ui.components.SignOutSetting
import org.jellyfin.mobile.common.ui.components.VaultTopBar
import org.jellyfin.mobile.common.ui.theme.VaultSpacing
import org.koin.compose.viewmodel.koinViewModel
import org.jellyfin.mobile.common.R as CommonR

/** What the settings screen shows: the reading settings every EPUB uses, downloads, and the account's. */
data class BookSettingsState(
    val fontScale: Float,
    val fontFamily: EpubFontFamily,
    val lineSpacing: Float,
    val removeDownloadsOnSignOut: Boolean,
    val downloadOnWifiOnly: Boolean,
    /** Space the downloaded books take; null until known, and then nothing is said about it. */
    val downloadSpace: DownloadSpace? = null,
    /** For About: the name as the bug form lists it, and the version. */
    val appName: String,
    val appVersion: String,
) {
    val textSettings get() = EpubTextSettings(fontScale, fontFamily, lineSpacing)
}

/** What the settings screen can change. */
data class BookSettingsActions(
    val onBack: () -> Unit = {},
    val onFontScale: (Float) -> Unit = {},
    val onFontFamily: (EpubFontFamily) -> Unit = {},
    val onLineSpacing: (Float) -> Unit = {},
    val onRemoveDownloadsOnSignOut: (Boolean) -> Unit = {},
    val onWifiOnly: (Boolean) -> Unit = {},
    val onRemoveAllDownloads: () -> Unit = {},
    val onSignOut: () -> Unit = {},
)

@Composable
fun BookSettingsScreen(state: BookSettingsState, actions: BookSettingsActions) {
    var picker by rememberSaveable { mutableStateOf<TextSetting?>(null) }
    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.systemBarsPadding()) {
            VaultTopBar(
                title = stringResource(R.string.settings),
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(CommonR.string.vault_back),
                        )
                    }
                },
            )
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = VaultSpacing.Large),
            ) {
                SettingsSectionHeader(stringResource(R.string.pref_category_reading))
                TextSettingRows(state.textSettings) { picker = it }
                Divider()
                DownloadsSection(state, actions)
                Divider()
                AccountSection(state.removeDownloadsOnSignOut, actions)
                Divider()
                AboutSettings(appName = state.appName, version = state.appVersion)
            }
        }
    }
    picker?.let {
        TextSettingPicker(
            setting = it,
            settings = state.textSettings,
            actions = TextSettingActions(actions.onFontScale, actions.onFontFamily, actions.onLineSpacing),
            onDismiss = { picker = null },
        )
    }
}

/** As TuneVault's: Wi-Fi only, what signing out does with downloads, and the space they take. */
@Composable
private fun DownloadsSection(state: BookSettingsState, actions: BookSettingsActions) {
    SettingsSectionHeader(stringResource(R.string.pref_category_downloads))
    SettingsSwitchRow(
        label = stringResource(CommonR.string.vault_pref_download_wifi_only),
        checked = state.downloadOnWifiOnly,
        onCheckedChange = actions.onWifiOnly,
    )
    SettingsSwitchRow(
        label = stringResource(CommonR.string.pref_remove_downloads_on_sign_out),
        summary = stringResource(CommonR.string.pref_remove_downloads_on_sign_out_summary),
        checked = state.removeDownloadsOnSignOut,
        onCheckedChange = actions.onRemoveDownloadsOnSignOut,
    )
    state.downloadSpace?.let { DownloadsSpaceSetting(it.bytes, it.hasDownloads, actions.onRemoveAllDownloads) }
}

@Composable
private fun AccountSection(removeDownloadsOnSignOut: Boolean, actions: BookSettingsActions) {
    SettingsSectionHeader(stringResource(R.string.pref_category_account))
    SignOutSetting(
        label = stringResource(R.string.pref_sign_out),
        confirmMessage = stringResource(R.string.sign_out_confirm),
        confirmDetail = stringResource(
            if (removeDownloadsOnSignOut) R.string.sign_out_removes_downloads else R.string.sign_out_keeps_downloads,
        ),
        confirmLabel = stringResource(R.string.sign_out_yes),
        onSignOut = actions.onSignOut,
    )
}

@Composable
fun BookSettingsRoute(onBack: () -> Unit, onSignedOut: () -> Unit, viewModel: BookSettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val downloadSpace by viewModel.downloadSpace.collectAsState()
    val signedOut by viewModel.signedOut.collectAsState()
    LaunchedEffect(viewModel) { viewModel.reload() }
    LaunchedEffect(signedOut) { if (signedOut) onSignedOut() }
    SignOutFailures(viewModel.signOutFailures)
    RemoveDownloadsFailures(viewModel.removeDownloadsFailures)
    BookSettingsScreen(
        state = state.copy(downloadSpace = downloadSpace),
        actions = BookSettingsActions(
            onBack = onBack,
            onFontScale = viewModel::setFontScale,
            onFontFamily = viewModel::setFontFamily,
            onLineSpacing = viewModel::setLineSpacing,
            onRemoveDownloadsOnSignOut = viewModel::setRemoveDownloadsOnSignOut,
            onWifiOnly = viewModel::setWifiOnly,
            onRemoveAllDownloads = viewModel::removeAllDownloads,
            onSignOut = viewModel::signOut,
        ),
    )
}
