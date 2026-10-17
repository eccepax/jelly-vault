package org.jellyfin.mobile.common.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.ui.theme.VaultSpacing

/** Picks one of [choices], scrolling when there are many; choosing reports it and closes, like dismissing does. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    choices: List<Choice<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    ListDialog(title = title, onDismiss = onDismiss, modifier = Modifier.selectableGroup()) {
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
}

/**
 * A titled list of rows, closed by tapping outside it. However long the list, only it scrolls: the
 * title stays put and the dialog stays within the screen (an AlertDialog grows past the screen
 * when its text scrolls).
 */
@Composable
fun ListDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colors.surface) {
            Column(modifier = Modifier.padding(vertical = VaultSpacing.XLarge)) {
                Text(
                    title,
                    style = MaterialTheme.typography.subtitle1,
                    modifier = Modifier.padding(horizontal = VaultSpacing.XLarge).padding(bottom = VaultSpacing.Large),
                )
                Column(
                    modifier = modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        // Rows pad themselves by Large, which lines them up with the title
                        .padding(horizontal = VaultSpacing.XLarge - VaultSpacing.Large),
                    content = content,
                )
            }
        }
    }
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

/** Tells the user something, with one button to close it. */
@Composable
fun NoticeDialog(message: String, onDismiss: () -> Unit, title: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(it) } },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
        backgroundColor = MaterialTheme.colors.surface,
    )
}

/** Asks for a line of text; confirming hands over what was typed, even if that's nothing. */
@Composable
fun TextFieldDialog(
    title: String,
    label: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onConfirm(text)
                },
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.vault_cancel)) }
        },
        backgroundColor = MaterialTheme.colors.surface,
    )
}
