package org.jellyfin.mobile.common.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.RadioButton
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.ui.formatBytes
import org.jellyfin.mobile.common.ui.theme.VaultSpacing

/** The heading over a group of settings, in the app's accent. */
@Composable
fun SettingsSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.subtitle2,
        color = MaterialTheme.colors.primary,
        modifier = modifier.padding(
            start = VaultSpacing.Large,
            end = VaultSpacing.Large,
            top = VaultSpacing.Large,
            bottom = VaultSpacing.XSmall,
        ),
    )
}

/** A line explaining the settings around it. */
@Composable
fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    SecondaryText(text, modifier = modifier.padding(horizontal = VaultSpacing.Large))
}

/** A setting that's on or off; the whole row toggles it. */
@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = VaultSpacing.Large, vertical = VaultSpacing.Medium),
    ) {
        RowText(label, summary, Modifier.weight(1f).padding(end = VaultSpacing.Large))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A setting that opens something to change it, showing its current value as the [summary]. */
@Composable
fun SettingsActionRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, summary: String? = null) {
    RowText(
        label,
        summary,
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = VaultSpacing.Large, vertical = VaultSpacing.Medium),
    )
}

/** Settings that take one of a few values, all shown at once. */
@Composable
fun <T> SettingsRadioGroup(
    choices: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.selectableGroup()) {
        choices.forEach { choice ->
            RadioRow(choice, selected = choice.value == selected, onClick = { onSelect(choice.value) })
        }
    }
}

@Composable
internal fun <T> RadioRow(choice: Choice<T>, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = VaultSpacing.Large, vertical = VaultSpacing.Small),
    ) {
        RadioButton(selected = selected, onClick = null)
        RowText(choice.label, choice.detail, Modifier.padding(start = VaultSpacing.Large))
    }
}

/**
 * Asks before something that can't be undone, in place rather than in a dialog, so a TV remote
 * reaches it like everything else on the screen.
 */
@Composable
fun InlineConfirm(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    destructive: Boolean = true,
) {
    Column(modifier = modifier.padding(horizontal = VaultSpacing.Large, vertical = VaultSpacing.Small)) {
        Text(message)
        if (detail != null) SecondaryText(detail)
        Row(
            horizontalArrangement = Arrangement.spacedBy(VaultSpacing.Small),
            modifier = Modifier.padding(top = VaultSpacing.Small),
        ) {
            Button(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.error,
                        contentColor = MaterialTheme.colors.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) { Text(confirmLabel) }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.vault_cancel)) }
        }
    }
}

/** How much space downloads take, and removing them all after asking in place ([InlineConfirm]). */
@Composable
fun DownloadsSpaceSetting(bytes: Long, onRemoveAll: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    val textModifier = Modifier.padding(horizontal = VaultSpacing.Large, vertical = VaultSpacing.Small)
    if (bytes == 0L) {
        Text(stringResource(R.string.downloads_nothing), modifier = textModifier)
        return
    }
    Text(stringResource(R.string.downloads_use, formatBytes(bytes)), modifier = textModifier)
    if (confirming) {
        InlineConfirm(
            message = stringResource(R.string.downloads_remove_all_confirm),
            confirmLabel = stringResource(R.string.downloads_remove_all_yes),
            onConfirm = {
                confirming = false
                onRemoveAll()
            },
            onCancel = { confirming = false },
            modifier = Modifier.bringIntoViewWhenShown(),
        )
    } else {
        OutlinedButton(
            onClick = { confirming = true },
            modifier = Modifier.padding(horizontal = VaultSpacing.Large, vertical = VaultSpacing.Small),
        ) {
            Text(stringResource(R.string.downloads_remove_all))
        }
    }
}

/** A confirmation opened near the bottom would otherwise appear below the fold, buttons unseen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.bringIntoViewWhenShown(): Modifier {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) { requester.bringIntoView() }
    return bringIntoViewRequester(requester)
}

/** Signs out, after asking in place ([InlineConfirm]); [confirmDetail] says what happens to downloads. */
@Composable
fun SignOutSetting(
    label: String,
    confirmMessage: String,
    confirmDetail: String,
    confirmLabel: String,
    onSignOut: () -> Unit,
) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    if (confirming) {
        InlineConfirm(
            message = confirmMessage,
            detail = confirmDetail,
            confirmLabel = confirmLabel,
            onConfirm = {
                confirming = false
                onSignOut()
            },
            onCancel = { confirming = false },
        )
    } else {
        OutlinedButton(
            onClick = { confirming = true },
            modifier = Modifier.padding(horizontal = VaultSpacing.Large, vertical = VaultSpacing.Small),
        ) {
            Text(label)
        }
    }
}

@Composable
private fun RowText(label: String, detail: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.body1)
        if (detail != null) SecondaryText(detail)
    }
}
