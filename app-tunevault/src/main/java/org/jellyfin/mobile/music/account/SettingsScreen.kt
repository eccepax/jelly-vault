package org.jellyfin.mobile.music.account

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.RadioButton
import androidx.compose.material.Surface
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.domain.StreamingQuality
import org.jellyfin.mobile.music.ui.formatBytes
import org.koin.compose.viewmodel.koinViewModel

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
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                SectionHeader(R.string.settings_about)
                Text(
                    stringResource(R.string.app_version, state.appVersion),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.subtitle2,
        color = MaterialTheme.colors.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun StreamingQualitySection(selected: StreamingQuality, onSelect: (StreamingQuality) -> Unit) {
    SectionHeader(R.string.streaming_quality)
    Note(R.string.streaming_quality_note)
    QualityOptions(selected, onSelect)
}

@Composable
private fun Note(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.caption,
        color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun DownloadsSection(state: SettingsUiState, actions: DownloadSettingsActions) {
    var confirmingRemoveAll by rememberSaveable { mutableStateOf(false) }
    SectionHeader(R.string.settings_downloads)
    Text(
        stringResource(R.string.download_quality),
        style = MaterialTheme.typography.body2,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
    Note(R.string.download_quality_note)
    QualityOptions(state.downloadQuality, actions.onQuality)
    SwitchRow(R.string.download_wifi_only, state.downloadOnWifiOnly, actions.onWifiOnly)
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (state.downloadBytes == 0L) {
            Text(stringResource(R.string.nothing_downloaded))
            return@Column
        }
        Text(stringResource(R.string.downloads_use, formatBytes(state.downloadBytes)))
        if (confirmingRemoveAll) {
            Text(stringResource(R.string.remove_all_confirm), modifier = Modifier.padding(top = 8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp).bringIntoViewWhenShown(),
            ) {
                Button(
                    onClick = {
                        confirmingRemoveAll = false
                        actions.onRemoveAll()
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error),
                ) { Text(stringResource(R.string.remove_all_yes)) }
                TextButton(onClick = { confirmingRemoveAll = false }) { Text(stringResource(R.string.cancel)) }
            }
        } else {
            OutlinedButton(onClick = { confirmingRemoveAll = true }, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.remove_all_downloads))
            }
        }
    }
}

/** A setting that's on or off: the whole row toggles it. */
@Composable
private fun SwitchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun QualityOptions(selected: StreamingQuality, onSelect: (StreamingQuality) -> Unit) {
    Column(modifier = Modifier.selectableGroup()) {
        StreamingQuality.entries.forEach { quality ->
            val (label, detail) = QUALITY_LABELS.getValue(quality)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = quality == selected, role = Role.RadioButton) { onSelect(quality) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                RadioButton(selected = quality == selected, onClick = null)
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(stringResource(label))
                    Text(
                        stringResource(detail),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountSection(state: SettingsUiState, onSignOut: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    SectionHeader(R.string.settings_account)
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        val account = state.account
        if (account != null) {
            Text(account.userName, style = MaterialTheme.typography.body1)
            Text(
                stringResource(R.string.account_server, account.serverName, account.serverUrl),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
            )
        }
        when {
            state.signingOut -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.signing_out))
            }
            confirming -> Column(modifier = Modifier.padding(top = 12.dp).bringIntoViewWhenShown()) {
                Text(
                    account?.let { stringResource(R.string.sign_out_confirm, it.serverName) }
                        ?: stringResource(R.string.sign_out_confirm_unknown),
                )
                Text(
                    stringResource(R.string.sign_out_removes_downloads),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(
                        onClick = {
                            confirming = false
                            onSignOut()
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error),
                    ) { Text(stringResource(R.string.sign_out_yes)) }
                    TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) }
                }
            }
            else -> OutlinedButton(onClick = { confirming = true }, modifier = Modifier.padding(top = 12.dp)) {
                Text(stringResource(R.string.sign_out))
            }
        }
    }
}

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
            onRemoveAll = viewModel::removeAllDownloads,
        ),
    )
}

/** A confirmation opened near the bottom would otherwise appear below the fold, buttons unseen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.bringIntoViewWhenShown(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { requester.bringIntoView() }
    return bringIntoViewRequester(requester)
}
