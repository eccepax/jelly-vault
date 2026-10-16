package org.jellyfin.mobile.books.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.app.LibraryViewMode
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.common.ui.components.Choice
import org.jellyfin.mobile.common.ui.components.ChoiceDialog
import org.jellyfin.mobile.common.ui.components.ConfirmDialog
import org.jellyfin.mobile.common.ui.components.CoverArt
import org.jellyfin.mobile.common.ui.components.DownloadedMark
import org.jellyfin.mobile.common.ui.components.EmptyState
import org.jellyfin.mobile.common.ui.components.ErrorState
import org.jellyfin.mobile.common.ui.components.LoadingState
import org.jellyfin.mobile.common.ui.components.MediaGridCard
import org.jellyfin.mobile.common.ui.components.MediaListRow
import org.jellyfin.mobile.common.ui.components.NoticeDialog
import org.jellyfin.mobile.common.ui.components.SearchField
import org.jellyfin.mobile.common.ui.components.VaultTopBar
import org.jellyfin.mobile.common.ui.content.ImageProvider
import org.jellyfin.mobile.common.ui.theme.VaultSpacing
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.mobile.common.R as CommonR

/** Everything the library screen shows. */
data class BookLibraryUiState(
    val books: List<BaseItemDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val downloadedIds: Set<String> = emptySet(),
    val viewMode: LibraryViewMode = LibraryViewMode.LIST,
    val query: String = "",
    val sort: BookSortOption = BookSortOption.TITLE,
    val progressFilter: ProgressFilter = ProgressFilter.ALL,
    val tagFilter: String? = null,
    val availableTags: List<String> = emptyList(),
    val downloadedOnly: Boolean = false,
)

/** What the library screen can do. */
data class BookLibraryActions(
    val onOpen: (BaseItemDto) -> Unit = {},
    val onDownload: (BaseItemDto) -> Unit = {},
    val onRemoveDownload: (BaseItemDto) -> Unit = {},
    val onToggleViewMode: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onSort: (BookSortOption) -> Unit = {},
    val onProgressFilter: (ProgressFilter) -> Unit = {},
    val onTagFilter: (String?) -> Unit = {},
    val onDownloadedOnly: (Boolean) -> Unit = {},
    val onSettings: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/** Which of the menu's pickers is open. */
private enum class LibraryPicker { SORT, PROGRESS, TAG }

@Composable
fun BookLibraryScreen(state: BookLibraryUiState, actions: BookLibraryActions) {
    var searching by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }
    var picker by rememberSaveable { mutableStateOf<LibraryPicker?>(null) }
    var removing by remember { mutableStateOf<BaseItemDto?>(null) }
    val onLongPress = { book: BaseItemDto ->
        if (book.isDownloaded(state)) removing = book else actions.onDownload(book)
    }

    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.systemBarsPadding()) {
            LibraryTopBar(
                state = state,
                actions = actions,
                searching = searching,
                onSearch = { searching = true },
                onCloseSearch = {
                    searching = false
                    actions.onQueryChange("")
                },
                onPicker = { picker = it },
            )
            if (searching) {
                SearchField(
                    query = state.query,
                    onQueryChange = actions.onQueryChange,
                    placeholder = stringResource(R.string.search_hint),
                    modifier = Modifier.testTag(VaultTestTags.SEARCH_FIELD),
                )
            }
            LibraryContent(state, actions, onLongPress)
        }
    }

    picker?.let { LibraryPickerDialog(it, state, actions, onDismiss = { picker = null }) }
    removing?.let { book ->
        ConfirmDialog(
            title = book.name,
            message = stringResource(R.string.remove_download_confirm),
            confirmLabel = stringResource(R.string.remove_download),
            onConfirm = { actions.onRemoveDownload(book) },
            onDismiss = { removing = null },
        )
    }
}

@Composable
private fun LibraryTopBar(
    state: BookLibraryUiState,
    actions: BookLibraryActions,
    searching: Boolean,
    onSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onPicker: (LibraryPicker) -> Unit,
) {
    VaultTopBar(
        title = stringResource(R.string.library),
        actions = {
            if (searching) {
                IconButton(onClick = onCloseSearch) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close_search))
                }
            } else {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
                }
            }
            IconButton(onClick = actions.onToggleViewMode) {
                if (state.viewMode == LibraryViewMode.LIST) {
                    Icon(Icons.Filled.GridView, contentDescription = stringResource(R.string.action_view_shelf))
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.ViewList,
                        contentDescription = stringResource(R.string.action_view_list),
                    )
                }
            }
            LibraryMenu(state, actions, onPicker)
        },
    )
}

@Composable
private fun LibraryMenu(state: BookLibraryUiState, actions: BookLibraryActions, onPicker: (LibraryPicker) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(CommonR.string.vault_more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            val item = @Composable { label: Int, onClick: () -> Unit ->
                DropdownMenuItem(
                    onClick = {
                        open = false
                        onClick()
                    },
                ) { Text(stringResource(label)) }
            }
            item(R.string.sort_by) { onPicker(LibraryPicker.SORT) }
            item(R.string.filter_progress) { onPicker(LibraryPicker.PROGRESS) }
            item(R.string.filter_tag) { onPicker(LibraryPicker.TAG) }
            // The item is what toggles, so it's the item that says whether it's on
            DropdownMenuItem(
                onClick = {
                    open = false
                    actions.onDownloadedOnly(!state.downloadedOnly)
                },
                modifier = Modifier
                    .testTag(VaultTestTags.DOWNLOADED_ONLY)
                    .semantics { toggleableState = ToggleableState(state.downloadedOnly) },
            ) {
                Text(stringResource(R.string.filter_downloaded_only), modifier = Modifier.weight(1f))
                Checkbox(checked = state.downloadedOnly, onCheckedChange = null)
            }
            item(R.string.settings, actions.onSettings)
        }
    }
}

@Composable
private fun LibraryContent(state: BookLibraryUiState, actions: BookLibraryActions, onLongPress: (BaseItemDto) -> Unit) {
    when {
        state.books.isEmpty() && state.loading -> LoadingState()
        state.books.isEmpty() && state.error != null -> ErrorState(state.error, onRetry = actions.onRetry)
        state.books.isEmpty() -> EmptyState(stringResource(R.string.library_empty), icon = BookPlaceholder)
        else -> Column {
            if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            when (state.viewMode) {
                LibraryViewMode.LIST -> BookList(state, actions.onOpen, onLongPress)
                LibraryViewMode.SHELF -> BookShelf(state, actions.onOpen, onLongPress)
            }
        }
    }
}

@Composable
private fun BookList(state: BookLibraryUiState, onOpen: (BaseItemDto) -> Unit, onLongPress: (BaseItemDto) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().testTag(VaultTestTags.LIBRARY_LIST)) {
        items(state.books, key = { it.id.toString() }) { book ->
            MediaListRow(
                title = book.name.orEmpty(),
                subtitle = book.author(),
                cover = { BookCover(book) },
                trailing = {
                    if (book.isDownloaded(state)) DownloadedMark(Modifier.testTag(VaultTestTags.DOWNLOADED_MARK))
                },
                modifier = Modifier
                    .testTag(VaultTestTags.LIBRARY_ITEM)
                    .combinedClickable(onClick = { onOpen(book) }, onLongClick = { onLongPress(book) }),
            )
        }
    }
}

@Composable
private fun BookShelf(state: BookLibraryUiState, onOpen: (BaseItemDto) -> Unit, onLongPress: (BaseItemDto) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(SHELF_MIN_CARD_WIDTH),
        contentPadding = PaddingValues(VaultSpacing.Large),
        horizontalArrangement = Arrangement.spacedBy(VaultSpacing.Large),
        verticalArrangement = Arrangement.spacedBy(VaultSpacing.Large),
        modifier = Modifier.fillMaxSize().testTag(VaultTestTags.LIBRARY_GRID),
    ) {
        items(state.books, key = { it.id.toString() }) { book ->
            MediaGridCard(
                title = book.name.orEmpty(),
                subtitle = book.author(),
                cover = {
                    Box {
                        BookCover(book)
                        if (book.isDownloaded(state)) {
                            DownloadedMark(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = VaultSpacing.Small)
                                    .testTag(VaultTestTags.DOWNLOADED_MARK),
                            )
                        }
                    }
                },
                onClick = { onOpen(book) },
                onLongClick = { onLongPress(book) },
                modifier = Modifier.testTag(VaultTestTags.LIBRARY_ITEM),
            )
        }
    }
}

@Composable
private fun BookCover(book: BaseItemDto) {
    CoverArt(
        url = book.id?.let { id ->
            ImageProvider.buildItemUri(id, ImageType.PRIMARY, book.imageTags?.get(ImageType.PRIMARY)).toString()
        },
        placeholder = BookPlaceholder,
        aspectRatio = BOOK_COVER_ASPECT,
    )
}

@Composable
private fun LibraryPickerDialog(
    picker: LibraryPicker,
    state: BookLibraryUiState,
    actions: BookLibraryActions,
    onDismiss: () -> Unit,
) {
    when (picker) {
        LibraryPicker.SORT -> ChoiceDialog(
            title = stringResource(R.string.sort_by),
            choices = listOf(
                Choice(BookSortOption.TITLE, stringResource(R.string.sort_title)),
                Choice(BookSortOption.AUTHOR, stringResource(R.string.sort_author)),
                Choice(BookSortOption.DATE_ADDED, stringResource(R.string.sort_date_added)),
            ),
            selected = state.sort,
            onSelect = actions.onSort,
            onDismiss = onDismiss,
        )
        LibraryPicker.PROGRESS -> ChoiceDialog(
            title = stringResource(R.string.filter_progress),
            choices = listOf(
                Choice(ProgressFilter.ALL, stringResource(R.string.filter_all)),
                Choice(ProgressFilter.UNREAD, stringResource(R.string.filter_unread)),
                Choice(ProgressFilter.IN_PROGRESS, stringResource(R.string.filter_in_progress)),
                Choice(ProgressFilter.FINISHED, stringResource(R.string.filter_finished)),
            ),
            selected = state.progressFilter,
            onSelect = actions.onProgressFilter,
            onDismiss = onDismiss,
        )
        // "All tags", then each tag a loaded book has; just a note when no book has any
        LibraryPicker.TAG -> if (state.availableTags.isEmpty()) {
            NoticeDialog(
                title = stringResource(R.string.filter_tag),
                message = stringResource(R.string.filter_no_tags),
                onDismiss = onDismiss,
            )
        } else {
            ChoiceDialog(
                title = stringResource(R.string.filter_tag),
                choices = listOf(Choice<String?>(null, stringResource(R.string.filter_all_tags))) +
                    state.availableTags.map { Choice(it, it) },
                selected = state.tagFilter,
                onSelect = actions.onTagFilter,
                onDismiss = onDismiss,
            )
        }
    }
}

private fun BaseItemDto.isDownloaded(state: BookLibraryUiState) = id?.toString() in state.downloadedIds

private fun BaseItemDto.author(): String? =
    (albumArtist ?: artists?.joinToString())?.takeIf { it.isNotBlank() }

private val BookPlaceholder = Icons.AutoMirrored.Filled.MenuBook
private const val BOOK_COVER_ASPECT = 2f / 3f
private val SHELF_MIN_CARD_WIDTH = 140.dp
