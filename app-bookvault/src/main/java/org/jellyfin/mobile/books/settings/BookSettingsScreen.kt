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
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
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
import org.jellyfin.mobile.books.reader.EPUB_FONT_FAMILY_PRESETS
import org.jellyfin.mobile.books.reader.EPUB_FONT_SCALE_PRESETS
import org.jellyfin.mobile.books.reader.EPUB_LINE_SPACING_PRESETS
import org.jellyfin.mobile.books.reader.formatFontFamilyLabel
import org.jellyfin.mobile.books.reader.formatFontScaleLabel
import org.jellyfin.mobile.books.reader.formatLineSpacingLabel
import org.jellyfin.mobile.common.ui.components.Choice
import org.jellyfin.mobile.common.ui.components.ChoiceDialog
import org.jellyfin.mobile.common.ui.components.InlineConfirm
import org.jellyfin.mobile.common.ui.components.SettingsActionRow
import org.jellyfin.mobile.common.ui.components.SettingsSectionHeader
import org.jellyfin.mobile.common.ui.components.SettingsSwitchRow
import org.jellyfin.mobile.common.ui.components.VaultTopBar
import org.jellyfin.mobile.common.ui.theme.VaultSpacing
import org.jellyfin.mobile.common.R as CommonR

/** What the settings screen shows: the reading settings every EPUB uses, and the account's. */
data class BookSettingsState(
    val fontScale: Float,
    val fontFamily: EpubFontFamily,
    val lineSpacing: Float,
    val removeDownloadsOnSignOut: Boolean,
)

/** What the settings screen can change. */
data class BookSettingsActions(
    val onBack: () -> Unit = {},
    val onFontScale: (Float) -> Unit = {},
    val onFontFamily: (EpubFontFamily) -> Unit = {},
    val onLineSpacing: (Float) -> Unit = {},
    val onRemoveDownloadsOnSignOut: (Boolean) -> Unit = {},
    val onSignOut: () -> Unit = {},
)

/** Which reading setting's picker is open. */
private enum class Picker { FONT_SCALE, FONT_FAMILY, LINE_SPACING }

@Composable
fun BookSettingsScreen(state: BookSettingsState, actions: BookSettingsActions) {
    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
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
                SettingsActionRow(
                    label = stringResource(R.string.text_settings_font_size),
                    summary = formatFontScaleLabel(state.fontScale),
                    onClick = { picker = Picker.FONT_SCALE },
                )
                SettingsActionRow(
                    label = stringResource(R.string.text_settings_font_family),
                    summary = formatFontFamilyLabel(state.fontFamily),
                    onClick = { picker = Picker.FONT_FAMILY },
                )
                SettingsActionRow(
                    label = stringResource(R.string.text_settings_line_spacing),
                    summary = formatLineSpacingLabel(state.lineSpacing),
                    onClick = { picker = Picker.LINE_SPACING },
                )
                Divider()
                AccountSection(state.removeDownloadsOnSignOut, actions)
            }
        }
    }
    picker?.let { ReadingPicker(it, state, actions, onDismiss = { picker = null }) }
}

@Composable
private fun ReadingPicker(
    picker: Picker,
    state: BookSettingsState,
    actions: BookSettingsActions,
    onDismiss: () -> Unit,
) {
    when (picker) {
        Picker.FONT_SCALE -> ChoiceDialog(
            title = stringResource(R.string.text_settings_font_size),
            choices = EPUB_FONT_SCALE_PRESETS.map { Choice(it, formatFontScaleLabel(it)) },
            selected = state.fontScale,
            onSelect = actions.onFontScale,
            onDismiss = onDismiss,
        )
        Picker.FONT_FAMILY -> ChoiceDialog(
            title = stringResource(R.string.text_settings_font_family),
            choices = EPUB_FONT_FAMILY_PRESETS.map { Choice(it, formatFontFamilyLabel(it)) },
            selected = state.fontFamily,
            onSelect = actions.onFontFamily,
            onDismiss = onDismiss,
        )
        Picker.LINE_SPACING -> ChoiceDialog(
            title = stringResource(R.string.text_settings_line_spacing),
            choices = EPUB_LINE_SPACING_PRESETS.map { Choice(it, formatLineSpacingLabel(it)) },
            selected = state.lineSpacing,
            onSelect = actions.onLineSpacing,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun AccountSection(removeDownloadsOnSignOut: Boolean, actions: BookSettingsActions) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    SettingsSectionHeader(stringResource(R.string.pref_category_account))
    SettingsSwitchRow(
        label = stringResource(CommonR.string.pref_remove_downloads_on_sign_out),
        summary = stringResource(CommonR.string.pref_remove_downloads_on_sign_out_summary),
        checked = removeDownloadsOnSignOut,
        onCheckedChange = actions.onRemoveDownloadsOnSignOut,
    )
    if (confirming) {
        InlineConfirm(
            message = stringResource(R.string.sign_out_confirm),
            detail = stringResource(
                if (removeDownloadsOnSignOut) R.string.sign_out_removes_downloads else R.string.sign_out_keeps_downloads,
            ),
            confirmLabel = stringResource(R.string.sign_out_yes),
            onConfirm = {
                confirming = false
                actions.onSignOut()
            },
            onCancel = { confirming = false },
        )
    } else {
        OutlinedButton(
            onClick = { confirming = true },
            modifier = Modifier.padding(horizontal = VaultSpacing.Large, vertical = VaultSpacing.Small),
        ) {
            Text(stringResource(R.string.pref_sign_out))
        }
    }
}
