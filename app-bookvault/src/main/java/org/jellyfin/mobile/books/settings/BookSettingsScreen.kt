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
import org.jellyfin.mobile.common.ui.components.SettingsSectionHeader
import org.jellyfin.mobile.common.ui.components.SettingsSwitchRow
import org.jellyfin.mobile.common.ui.components.SignOutSetting
import org.jellyfin.mobile.common.ui.components.VaultTopBar
import org.jellyfin.mobile.common.ui.theme.VaultSpacing
import org.jellyfin.mobile.common.R as CommonR

/** What the settings screen shows: the reading settings every EPUB uses, and the account's. */
data class BookSettingsState(
    val fontScale: Float,
    val fontFamily: EpubFontFamily,
    val lineSpacing: Float,
    val removeDownloadsOnSignOut: Boolean,
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
                AccountSection(state.removeDownloadsOnSignOut, actions)
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

@Composable
private fun AccountSection(removeDownloadsOnSignOut: Boolean, actions: BookSettingsActions) {
    SettingsSectionHeader(stringResource(R.string.pref_category_account))
    SettingsSwitchRow(
        label = stringResource(CommonR.string.pref_remove_downloads_on_sign_out),
        summary = stringResource(CommonR.string.pref_remove_downloads_on_sign_out_summary),
        checked = removeDownloadsOnSignOut,
        onCheckedChange = actions.onRemoveDownloadsOnSignOut,
    )
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
