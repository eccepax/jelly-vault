package org.jellyfin.mobile.common.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.common.R

/** Picks one of [choices]; choosing reports it and closes, like dismissing does. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                choices.forEach { choice ->
                    RadioRow(
                        choice,
                        selected = choice.value == selected,
                        onClick = {
                            onSelect(choice.value)
                            onDismiss()
                        },
                    )
                }
            }
        },
        buttons = {},
        backgroundColor = MaterialTheme.colors.surface,
    )
}

/** Asks before doing something, where a screen has no room to ask in place ([InlineConfirm]). */
@Composable
fun ConfirmDialog(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm()
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.vault_cancel)) }
        },
        backgroundColor = MaterialTheme.colors.surface,
    )
}
