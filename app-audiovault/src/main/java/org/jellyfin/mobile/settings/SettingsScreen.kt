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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.ui.components.AboutSettings
import org.jellyfin.mobile.common.ui.components.ChoiceDialog
import org.jellyfin.mobile.common.ui.components.SettingsActionRow
import org.jellyfin.mobile.common.ui.components.SettingsSectionHeader
import org.jellyfin.mobile.common.ui.components.SettingsSwitchRow
import org.jellyfin.mobile.common.ui.components.SignOutFailures
import org.jellyfin.mobile.common.ui.components.SignOutSetting
import org.jellyfin.mobile.common.ui.components.VaultTopBar
import org.jellyfin.mobile.common.ui.theme.VaultSpacing
import org.jellyfin.mobile.downloads.DownloadMethod
import org.jellyfin.mobile.downloads.downloadNetworks
import org.koin.compose.viewmodel.koinViewModel
import org.jellyfin.mobile.common.R as CommonR

/** What the settings screen shows. */
data class SettingsUiState(
    val holdToSpeedUp: Boolean,
    val skipSilence: Boolean,
    val flatLibrary: Boolean,
    /** The networks downloads may use; none chosen yet means Wi-Fi only, as downloads treat it. */
    @DownloadMethod val downloadMethod: Int?,
    val removeDownloadsOnSignOut: Boolean,
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
    val onDownloadMethod: (Int) -> Unit = {},
    val onRemoveDownloadsOnSignOut: (Boolean) -> Unit = {},
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

@Composable
private fun DownloadsSection(state: SettingsUiState, actions: SettingsActions) {
    var choosingNetwork by rememberSaveable { mutableStateOf(false) }
    val networks = downloadNetworks()
    val network = networks.first { it.value == (state.downloadMethod ?: DownloadMethod.WIFI_ONLY) }
    SettingsSectionHeader(stringResource(R.string.pref_category_downloads))
    SettingsActionRow(
        label = stringResource(R.string.network_title),
        summary = network.label,
        onClick = { choosingNetwork = true },
    )
    SettingsSwitchRow(
        label = stringResource(CommonR.string.pref_remove_downloads_on_sign_out),
        summary = stringResource(CommonR.string.pref_remove_downloads_on_sign_out_summary),
        checked = state.removeDownloadsOnSignOut,
        onCheckedChange = actions.onRemoveDownloadsOnSignOut,
    )
    if (choosingNetwork) {
        ChoiceDialog(
            title = stringResource(R.string.network_title),
            choices = networks,
            selected = network.value,
            onSelect = actions.onDownloadMethod,
            onDismiss = { choosingNetwork = false },
        )
    }
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
    val signedOut by viewModel.signedOut.collectAsState()
    LaunchedEffect(viewModel) { viewModel.reload() }
    LaunchedEffect(signedOut) { if (signedOut) onSignedOut() }
    SignOutFailures(viewModel.signOutFailures)
    SettingsScreen(
        state = state,
        actions = SettingsActions(
            onBack = onBack,
            onHoldToSpeedUp = viewModel::setHoldToSpeedUp,
            onSkipSilence = viewModel::setSkipSilence,
            onFlatLibrary = viewModel::setFlatLibrary,
            onDownloadMethod = viewModel::setDownloadMethod,
            onRemoveDownloadsOnSignOut = viewModel::setRemoveDownloadsOnSignOut,
            onSignOut = viewModel::signOut,
        ),
    )
}
