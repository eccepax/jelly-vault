package org.jellyfin.mobile.books.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.app.EpubFontFamily
import org.jellyfin.mobile.books.data.entity.BookBookmarkEntity
import org.jellyfin.mobile.common.ui.components.Choice
import org.jellyfin.mobile.common.ui.components.ChoiceDialog
import org.jellyfin.mobile.common.ui.components.ListDialog
import org.jellyfin.mobile.common.ui.components.LoadingState
import org.jellyfin.mobile.common.ui.components.NoticeDialog
import org.jellyfin.mobile.common.ui.components.SecondaryText
import org.jellyfin.mobile.common.ui.components.TextFieldDialog
import org.jellyfin.mobile.common.ui.components.VaultTopBar
import org.jellyfin.mobile.common.ui.theme.VaultAlpha
import org.jellyfin.mobile.common.ui.theme.VaultSpacing
import java.text.DateFormat
import java.util.Date
import org.jellyfin.mobile.common.R as CommonR

/** Everything the reader shows around the pages themselves. */
data class BookReaderUiState(
    val bookType: BookType = BookType.UNKNOWN,
    val loading: Boolean = false,
    val scrollMode: Boolean = true,
    val nightMode: Boolean = false,
    /** The chapter (EPUB) or page (PDF) being read, from 0. */
    val chapter: Int = 0,
    val chapterCount: Int = 0,
    val chapterTitle: String? = null,
    val tableOfContents: List<EpubTocEntry> = emptyList(),
    val bookmarks: List<BookBookmarkEntity> = emptyList(),
    val textSettings: EpubTextSettings = EpubTextSettings(),
)

/** What the reader can do. */
data class BookReaderActions(
    val onBack: () -> Unit = {},
    val onToggleScrollMode: () -> Unit = {},
    val onToggleNightMode: () -> Unit = {},
    /** Goes to a chapter (EPUB) or page (PDF), from 0. */
    val onGoTo: (Int) -> Unit = {},
    val onFontScale: (Float) -> Unit = {},
    val onFontFamily: (EpubFontFamily) -> Unit = {},
    val onLineSpacing: (Float) -> Unit = {},
    val onAddBookmark: (String) -> Unit = {},
    val onOpenBookmark: (BookBookmarkEntity) -> Unit = {},
    val onDeleteBookmark: (Long) -> Unit = {},
)

/** Which of the reader's dialogs is open. */
private enum class ReaderDialog { TEXT_SETTINGS, CONTENTS, ADD_BOOKMARK, BOOKMARKS }

/** The reader: [pages] shows the book itself, with the top bar above it and, reading an EPUB as a scroll, the chapter bar below. */
@Composable
fun BookReaderScreen(state: BookReaderUiState, actions: BookReaderActions, pages: @Composable () -> Unit) {
    var dialog by rememberSaveable { mutableStateOf<ReaderDialog?>(null) }
    var textSetting by rememberSaveable { mutableStateOf<TextSetting?>(null) }

    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.systemBarsPadding()) {
            ReaderTopBar(state, actions, onDialog = { dialog = it })
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                pages()
                if (state.loading) LoadingState(Modifier.background(MaterialTheme.colors.background))
            }
            if (state.bookType == BookType.EPUB && state.scrollMode) ChapterBar(state, actions.onGoTo)
        }
    }

    val dismiss = { dialog = null }
    when (dialog) {
        ReaderDialog.TEXT_SETTINGS -> TextSettingsDialog(state.textSettings, onDismiss = dismiss) {
            dialog = null
            textSetting = it
        }
        ReaderDialog.CONTENTS -> ContentsDialog(state, actions.onGoTo, dismiss)
        ReaderDialog.ADD_BOOKMARK -> TextFieldDialog(
            title = stringResource(R.string.add_bookmark_title),
            label = stringResource(R.string.add_bookmark_note_hint),
            confirmLabel = stringResource(R.string.bookmark_save),
            onConfirm = actions.onAddBookmark,
            onDismiss = dismiss,
        )
        ReaderDialog.BOOKMARKS -> BookmarksDialog(state.bookmarks, actions, dismiss)
        null -> Unit
    }
    textSetting?.let {
        TextSettingPicker(
            setting = it,
            settings = state.textSettings,
            actions = TextSettingActions(actions.onFontScale, actions.onFontFamily, actions.onLineSpacing),
            onDismiss = { textSetting = null },
        )
    }
}

@Composable
private fun ReaderTopBar(state: BookReaderUiState, actions: BookReaderActions, onDialog: (ReaderDialog) -> Unit) {
    VaultTopBar(
        title = "",
        navigationIcon = {
            IconButton(onClick = actions.onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(CommonR.string.vault_back),
                )
            }
        },
        actions = {
            // Each button shows, and says, what tapping it switches to
            IconButton(onClick = actions.onToggleScrollMode) {
                if (state.scrollMode) {
                    Icon(Icons.Filled.AutoStories, contentDescription = stringResource(R.string.reader_page_mode))
                } else {
                    Icon(Icons.Filled.ViewDay, contentDescription = stringResource(R.string.reader_scroll_mode))
                }
            }
            IconButton(onClick = actions.onToggleNightMode) {
                if (state.nightMode) {
                    Icon(Icons.Filled.LightMode, contentDescription = stringResource(R.string.reader_night_mode_off))
                } else {
                    Icon(Icons.Filled.DarkMode, contentDescription = stringResource(R.string.reader_night_mode_on))
                }
            }
            ReaderMenu(contentsEnabled = state.bookType != BookType.UNKNOWN, onDialog = onDialog)
        },
    )
}

@Composable
private fun ReaderMenu(contentsEnabled: Boolean, onDialog: (ReaderDialog) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(CommonR.string.vault_more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val item = @Composable { label: Int, target: ReaderDialog, enabled: Boolean ->
                DropdownMenuItem(
                    onClick = {
                        open = false
                        onDialog(target)
                    },
                    enabled = enabled,
                ) { Text(stringResource(label)) }
            }
            item(R.string.text_settings_title, ReaderDialog.TEXT_SETTINGS, true)
            item(R.string.contents_title, ReaderDialog.CONTENTS, contentsEnabled)
            item(R.string.add_bookmark_action, ReaderDialog.ADD_BOOKMARK, true)
            item(R.string.bookmarks_title, ReaderDialog.BOOKMARKS, true)
        }
    }
}

/** The chapter's name and position, with arrows only where there's a chapter to go to. */
@Composable
private fun ChapterBar(state: BookReaderUiState, onGoTo: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Divider(color = MaterialTheme.colors.onBackground.copy(alpha = VaultAlpha.DIVIDER))
        Row(
            modifier = Modifier.heightIn(min = CHAPTER_BAR_HEIGHT).padding(horizontal = VaultSpacing.XSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onGoTo(state.chapter - 1) }, enabled = state.chapter > 0) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.chapter_prev),
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(vertical = VaultSpacing.XSmall),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                state.chapterTitle?.let {
                    Text(it, style = MaterialTheme.typography.body2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SecondaryText(
                    stringResource(R.string.chapter_position, state.chapter + 1, state.chapterCount),
                    maxLines = 1,
                )
            }
            IconButton(onClick = { onGoTo(state.chapter + 1) }, enabled = state.chapter < state.chapterCount - 1) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.chapter_next),
                )
            }
        }
    }
}

/** Each text setting with its value; choosing one hands over to its picker. */
@Composable
private fun TextSettingsDialog(settings: EpubTextSettings, onDismiss: () -> Unit, onPick: (TextSetting) -> Unit) {
    ListDialog(title = stringResource(R.string.text_settings_title), onDismiss = onDismiss) {
        TextSettingRows(settings, onPick)
    }
}

/** An EPUB's chapters, marking the one being read; a PDF asks for a page instead. */
@Composable
private fun ContentsDialog(state: BookReaderUiState, onGoTo: (Int) -> Unit, onDismiss: () -> Unit) {
    when (state.bookType) {
        BookType.EPUB -> if (state.tableOfContents.isEmpty()) {
            NoticeDialog(
                title = stringResource(R.string.contents_title),
                message = stringResource(R.string.contents_empty),
                onDismiss = onDismiss,
            )
        } else {
            val toc = state.tableOfContents
            // The entry being read is the last that starts at or before the current chapter
            val current = toc.indexOfLast { it.spineIndex <= state.chapter }
            ChoiceDialog(
                title = stringResource(R.string.contents_title),
                choices = toc.mapIndexed { index, entry -> Choice(index, entry.title) },
                selected = current,
                onSelect = { onGoTo(toc[it].spineIndex) },
                onDismiss = onDismiss,
            )
        }
        BookType.PDF -> TextFieldDialog(
            title = stringResource(R.string.go_to_page_title),
            label = stringResource(R.string.go_to_page_hint),
            confirmLabel = stringResource(R.string.go),
            onConfirm = { typed ->
                typed.trim().toIntOrNull()?.let { page -> onGoTo((page - 1).coerceIn(0, state.chapterCount - 1)) }
            },
            onDismiss = onDismiss,
            keyboardType = KeyboardType.Number,
        )
        BookType.UNKNOWN -> Unit
    }
}

/** The saved bookmarks: tapping one goes to it, and each can be deleted. */
@Composable
private fun BookmarksDialog(bookmarks: List<BookBookmarkEntity>, actions: BookReaderActions, onDismiss: () -> Unit) {
    if (bookmarks.isEmpty()) {
        NoticeDialog(
            title = stringResource(R.string.bookmarks_title),
            message = stringResource(R.string.no_bookmarks_yet),
            onDismiss = onDismiss,
        )
        return
    }
    ListDialog(title = stringResource(R.string.bookmarks_title), onDismiss = onDismiss) {
        bookmarks.forEach { bookmark ->
            BookmarkRow(
                bookmark,
                onOpen = {
                    onDismiss()
                    actions.onOpenBookmark(bookmark)
                },
                onDelete = { actions.onDeleteBookmark(bookmark.id) },
            )
        }
    }
}

@Composable
private fun BookmarkRow(bookmark: BookBookmarkEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
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
            ).format(Date(bookmark.createdAt))
            SecondaryText(listOfNotNull(saved, bookmark.note?.takeIf { it.isNotBlank() }).joinToString(" — "))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_bookmark, bookmark.label))
        }
    }
}

private val CHAPTER_BAR_HEIGHT = 56.dp
