package org.jellyfin.mobile.settings

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.R
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

/** What the settings screen shows. */
data class SettingsUiState(
    val holdToSpeedUp: Boolean,
    val skipSilence: Boolean,
    val flatLibrary: Boolean,
    val downloadOnWifiOnly: Boolean,
    val removeDownloadsOnSignOut: Boolean,
    /** The space downloads take; null until known, and then nothing is said about it. */
    val downloadSpace: DownloadSpace? = null,
    /** For About: the name as the bug form lists it, and the version. */
    val appName: String,
    val appVersion: String,
)

/** What the settings screen can change. */
data class SettingsActions(
    val onBack: () -> Unit = {},
    val onHoldToSpeedUp: (Boolean) -> Unit = {},
    val onSkipSilence: (Boolean) -> Unit = {},
    val onFlatLibrary: (Boolean) -> Unit = {},
    val onWifiOnly: (Boolean) -> Unit = {},
    val onRemoveDownloadsOnSignOut: (Boolean) -> Unit = {},
    val onRemoveAllDownloads: () -> Unit = {},
    val onSignOut: () -> Unit = {},
)

@Composable
fun SettingsScreen(state: SettingsUiState, actions: SettingsActions) {
    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.systemBarsPadding()) {
            VaultTopBar(
                title = stringResource(R.string.activity_name_settings),
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
                PlaybackSection(state, actions)
                Divider()
                LibrarySection(state, actions)
                Divider()
                DownloadsSection(state, actions)
                Divider()
                AccountSection(state, actions)
                Divider()
                AboutSettings(appName = state.appName, version = state.appVersion)
            }
        }
    }
}

@Composable
private fun PlaybackSection(state: SettingsUiState, actions: SettingsActions) {
    SettingsSectionHeader(stringResource(R.string.pref_category_playback))
    SettingsSwitchRow(
        label = stringResource(R.string.pref_exoplayer_allow_press_speed_up),
        summary = stringResource(R.string.pref_exoplayer_allow_press_speed_up_summary),
        checked = state.holdToSpeedUp,
        onCheckedChange = actions.onHoldToSpeedUp,
    )
    SettingsSwitchRow(
        label = stringResource(R.string.pref_skip_silence_title),
        summary = stringResource(R.string.pref_skip_silence_summary),
        checked = state.skipSilence,
        onCheckedChange = actions.onSkipSilence,
    )
}

@Composable
private fun LibrarySection(state: SettingsUiState, actions: SettingsActions) {
    SettingsSectionHeader(stringResource(R.string.pref_category_library))
    SettingsSwitchRow(
        label = stringResource(R.string.pref_library_global_flatten_title),
        summary = stringResource(
            if (state.flatLibrary) {
                R.string.pref_library_global_flatten_summary_on
            } else {
                R.string.pref_library_global_flatten_summary_off
            },
        ),
        checked = state.flatLibrary,
        onCheckedChange = actions.onFlatLibrary,
    )
}

/** As BookVault's and TuneVault's: Wi-Fi only, what signing out does with downloads, and the space they take. */
@Composable
private fun DownloadsSection(state: SettingsUiState, actions: SettingsActions) {
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
private fun AccountSection(state: SettingsUiState, actions: SettingsActions) {
    SettingsSectionHeader(stringResource(R.string.pref_category_account))
    SignOutSetting(
        label = stringResource(R.string.pref_sign_out),
        confirmMessage = stringResource(R.string.sign_out_confirm),
        confirmDetail = stringResource(
            if (state.removeDownloadsOnSignOut) R.string.sign_out_removes_downloads else R.string.sign_out_keeps_downloads,
        ),
        confirmLabel = stringResource(R.string.sign_out_yes),
        onSignOut = actions.onSignOut,
    )
}

/** Settings, saved through [viewModel]; [onSignedOut] once signing out is done, to start the app over. */
@Composable
fun SettingsRoute(onBack: () -> Unit, onSignedOut: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val downloadSpace by viewModel.downloadSpace.collectAsState()
    val signedOut by viewModel.signedOut.collectAsState()
    LaunchedEffect(viewModel) { viewModel.reload() }
    LaunchedEffect(signedOut) { if (signedOut) onSignedOut() }
    SignOutFailures(viewModel.signOutFailures)
    RemoveDownloadsFailures(viewModel.removeDownloadsFailures)
    SettingsScreen(
        state = state.copy(downloadSpace = downloadSpace),
        actions = SettingsActions(
            onBack = onBack,
            onHoldToSpeedUp = viewModel::setHoldToSpeedUp,
            onSkipSilence = viewModel::setSkipSilence,
            onFlatLibrary = viewModel::setFlatLibrary,
            onWifiOnly = viewModel::setWifiOnly,
            onRemoveDownloadsOnSignOut = viewModel::setRemoveDownloadsOnSignOut,
            onRemoveAllDownloads = viewModel::removeAllDownloads,
            onSignOut = viewModel::signOut,
        ),
    )
}
