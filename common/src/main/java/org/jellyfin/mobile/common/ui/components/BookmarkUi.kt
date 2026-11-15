package org.jellyfin.mobile.common.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.common.R
import org.jellyfin.mobile.common.ui.theme.VaultSpacing
import java.text.DateFormat
import java.util.Date

/** A bookmark as the list shows it: where it is ([label]), the listener's or reader's [note], and when it was saved. */
data class BookmarkUi(val id: Long, val label: String, val note: String?, val savedAt: Long)

/**
 * The saved bookmarks: tapping one closes the list and goes to it, and each can be deleted, and its
 * note changed when [onEditNote] is given. With none, says so.
 */
@Composable
fun BookmarksDialog(
    bookmarks: List<BookmarkUi>,
    onOpen: (id: Long) -> Unit,
    onDelete: (id: Long) -> Unit,
    onDismiss: () -> Unit,
    onEditNote: ((id: Long, note: String) -> Unit)? = null,
) {
    if (bookmarks.isEmpty()) {
        NoticeDialog(
            title = stringResource(R.string.bookmarks_title),
            message = stringResource(R.string.no_bookmarks_yet),
            onDismiss = onDismiss,
        )
        return
    }
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    val edited = bookmarks.firstOrNull { it.id == editing }
    if (edited != null && onEditNote != null) {
        TextFieldDialog(
            title = edited.label,
            label = stringResource(R.string.add_bookmark_note_hint),
            confirmLabel = stringResource(R.string.bookmark_save),
            onConfirm = { onEditNote(edited.id, it) },
            onDismiss = { editing = null },
            initialText = edited.note.orEmpty(),
        )
        return
    }
    ListDialog(title = stringResource(R.string.bookmarks_title), onDismiss = onDismiss) {
        bookmarks.forEach { bookmark ->
            BookmarkRow(
                bookmark,
                onOpen = {
                    onDismiss()
                    onOpen(bookmark.id)
                },
                onDelete = { onDelete(bookmark.id) },
                onEdit = onEditNote?.let { { editing = bookmark.id } },
            )
        }
    }
}

/** Asks for a note (optional) for a new bookmark; saving hands it over, empty when none was typed. */
@Composable
fun AddBookmarkDialog(onSave: (note: String) -> Unit, onDismiss: () -> Unit) {
    TextFieldDialog(
        title = stringResource(R.string.add_bookmark_title),
        label = stringResource(R.string.add_bookmark_note_hint),
        confirmLabel = stringResource(R.string.bookmark_save),
        onConfirm = onSave,
        onDismiss = onDismiss,
    )
}

@Composable
private fun BookmarkRow(bookmark: BookmarkUi, onOpen: () -> Unit, onDelete: () -> Unit, onEdit: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(
            onClick = onOpen,
        ).padding(start = VaultSpacing.Large, top = VaultSpacing.Small, bottom = VaultSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(bookmark.label, style = MaterialTheme.typography.body1)
            val saved = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT,
            ).format(Date(bookmark.savedAt))
            SecondaryText(listOfNotNull(saved, bookmark.note?.takeIf { it.isNotBlank() }).joinToString(" — "))
        }
        if (onEdit != null) {
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.edit_bookmark_note, bookmark.label),
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_bookmark, bookmark.label))
        }
    }
}
