package org.jellyfin.mobile.music.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.common.ui.components.AboutSettings
import org.jellyfin.mobile.common.ui.components.Choice
import org.jellyfin.mobile.common.ui.components.DownloadsSpaceSetting
import org.jellyfin.mobile.common.ui.components.InlineConfirm
import org.jellyfin.mobile.common.ui.components.SecondaryText
import org.jellyfin.mobile.common.ui.components.SettingsNote
import org.jellyfin.mobile.common.ui.components.SettingsRadioGroup
import org.jellyfin.mobile.common.ui.components.SettingsSectionHeader
import org.jellyfin.mobile.common.ui.components.SettingsSwitchRow
import org.jellyfin.mobile.common.ui.components.VaultTopBar
import org.jellyfin.mobile.common.ui.components.bringIntoViewWhenShown
import org.jellyfin.mobile.common.ui.theme.VaultSpacing
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.domain.StreamingQuality
import org.koin.compose.viewmodel.koinViewModel
import org.jellyfin.mobile.common.R as CommonR

private val QUALITY_LABELS = mapOf(
    StreamingQuality.ORIGINAL to (R.string.quality_original to R.string.quality_original_detail),
    StreamingQuality.HIGH to (R.string.quality_high to R.string.quality_high_detail),
    StreamingQuality.NORMAL to (R.string.quality_normal to R.string.quality_normal_detail),
    StreamingQuality.LOW to (R.string.quality_low to R.string.quality_low_detail),
)

/** What the Downloads section can change. */
data class DownloadSettingsActions(
    val onQuality: (StreamingQuality) -> Unit = {},
    val onWifiOnly: (Boolean) -> Unit = {},
    val onRemoveOnSignOut: (Boolean) -> Unit = {},
    val onRemoveAll: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onStreamingQuality: (StreamingQuality) -> Unit,
    onSignOut: () -> Unit,
    onShowSleepTimer: (Boolean) -> Unit = {},
    onVolumeLevelling: (Boolean) -> Unit = {},
    downloads: DownloadSettingsActions = DownloadSettingsActions(),
) {
    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.systemBarsPadding()) {
            VaultTopBar(
                title = stringResource(R.string.settings_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                StreamingQualitySection(state.streamingQuality, onStreamingQuality)
                SwitchRow(R.string.volume_levelling, state.volumeLevelling, onVolumeLevelling)
                Note(R.string.volume_levelling_note)
                Divider()
                SectionHeader(R.string.settings_now_playing)
                SwitchRow(R.string.show_sleep_timer, state.showSleepTimer, onShowSleepTimer)
                Divider()
                DownloadsSection(state, downloads)
                Divider()
                AccountSection(state, onSignOut)
                Divider()
                AboutSettings(appName = state.appName, version = state.appVersion)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: Int) {
    SettingsSectionHeader(stringResource(title))
}

@Composable
private fun StreamingQualitySection(selected: StreamingQuality, onSelect: (StreamingQuality) -> Unit) {
    SectionHeader(R.string.streaming_quality)
    Note(R.string.streaming_quality_note)
    QualityOptions(selected, onSelect)
}

@Composable
private fun Note(text: Int) {
    SettingsNote(stringResource(text))
}

@Composable
private fun DownloadsSection(state: SettingsUiState, actions: DownloadSettingsActions) {
    SectionHeader(R.string.settings_downloads)
    Text(
        stringResource(R.string.download_quality),
        style = MaterialTheme.typography.body2,
        modifier = Modifier.padding(horizontal = VaultSpacing.Large),
    )
    Note(R.string.download_quality_note)
    QualityOptions(state.downloadQuality, actions.onQuality)
    SwitchRow(CommonR.string.pref_download_wifi_only, state.downloadOnWifiOnly, actions.onWifiOnly)
    SwitchRow(
        CommonR.string.pref_remove_downloads_on_sign_out,
        state.removeDownloadsOnSignOut,
        actions.onRemoveOnSignOut,
    )
    DownloadsSpaceSetting(state.downloadBytes, actions.onRemoveAll)
}

/** A setting that's on or off: the whole row toggles it. */
@Composable
private fun SwitchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingsSwitchRow(stringResource(label), checked, onChange)
}

@Composable
private fun QualityOptions(selected: StreamingQuality, onSelect: (StreamingQuality) -> Unit) {
    SettingsRadioGroup(
        choices = StreamingQuality.entries.map { quality ->
            val (label, detail) = QUALITY_LABELS.getValue(quality)
            Choice(quality, stringResource(label), stringResource(detail))
        },
        selected = selected,
        onSelect = onSelect,
    )
}

@Composable
private fun AccountSection(state: SettingsUiState, onSignOut: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    SectionHeader(R.string.settings_account)
    val account = state.account
    if (account != null) {
        Column(modifier = Modifier.padding(SettingsTextPadding)) {
            Text(account.userName, style = MaterialTheme.typography.body1)
            SecondaryText(stringResource(R.string.account_server, account.serverName, account.serverUrl))
        }
    }
    when {
        state.signingOut -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.Medium),
            modifier = Modifier.padding(SettingsTextPadding),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.signing_out))
        }
        confirming -> InlineConfirm(
            message = account?.let { stringResource(R.string.sign_out_confirm, it.serverName) }
                ?: stringResource(R.string.sign_out_confirm_unknown),
            detail = stringResource(R.string.sign_out_removes_downloads),
            confirmLabel = stringResource(R.string.sign_out_yes),
            onConfirm = {
                confirming = false
                onSignOut()
            },
            onCancel = { confirming = false },
            modifier = Modifier.bringIntoViewWhenShown(),
        )
        else -> OutlinedButton(
            onClick = { confirming = true },
            modifier = Modifier.padding(horizontal = VaultSpacing.Large, vertical = VaultSpacing.Small),
        ) {
            Text(stringResource(R.string.sign_out))
        }
    }
}

/** Where plain text sits among the settings rows. */
private val SettingsTextPadding = PaddingValues(horizontal = VaultSpacing.Large, vertical = VaultSpacing.Small)

@Composable
fun SettingsRoute(onBack: () -> Unit, onSignedOut: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    val signedOut by viewModel.signedOut.collectAsState()
    LaunchedEffect(signedOut) { if (signedOut) onSignedOut() }
    SettingsScreen(
        state = state,
        onBack = onBack,
        onStreamingQuality = viewModel::setStreamingQuality,
        onSignOut = viewModel::signOut,
        onShowSleepTimer = viewModel::setShowSleepTimer,
        onVolumeLevelling = viewModel::setVolumeLevelling,
        downloads = DownloadSettingsActions(
            onQuality = viewModel::setDownloadQuality,
            onWifiOnly = viewModel::setDownloadOnWifiOnly,
            onRemoveOnSignOut = viewModel::setRemoveDownloadsOnSignOut,
            onRemoveAll = viewModel::removeAllDownloads,
        ),
    )
}
