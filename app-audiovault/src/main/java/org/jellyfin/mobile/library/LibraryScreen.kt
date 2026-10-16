package org.jellyfin.mobile.library

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Checkbox
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.ui.VaultTestTags
import org.jellyfin.mobile.common.ui.components.Choice
import org.jellyfin.mobile.common.ui.components.ChoiceDialog
import org.jellyfin.mobile.common.ui.components.CoverArt
import org.jellyfin.mobile.common.ui.components.DownloadedMark
import org.jellyfin.mobile.common.ui.components.EmptyState
import org.jellyfin.mobile.common.ui.components.ErrorState
import org.jellyfin.mobile.common.ui.components.ListDialog
import org.jellyfin.mobile.common.ui.components.LoadingState
import org.jellyfin.mobile.common.ui.components.MediaListRow
import org.jellyfin.mobile.common.ui.components.MiniPlayerBar
import org.jellyfin.mobile.common.ui.components.SearchField
import org.jellyfin.mobile.common.ui.components.SettingsActionRow
import org.jellyfin.mobile.common.ui.components.VaultTopBar
import java.util.UUID
import kotlin.math.roundToInt
import org.jellyfin.mobile.common.R as CommonR

/** Everything the library screen shows. */
data class LibraryUiState(
    val title: String,
    val canGoUp: Boolean = false,
    val items: List<LibraryItem> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val downloadedIds: Set<String> = emptySet(),
    val hiddenIds: Set<String> = emptySet(),
    val query: String = "",
    val sort: SortOption = SortOption.TITLE,
    val downloadedOnly: Boolean = false,
    val showHidden: Boolean = false,
    /** What holding an item offers, while that menu is open. */
    val itemMenu: ItemMenu? = null,
    /** What's loaded in the player, for the mini player; null when nothing is. */
    val nowPlaying: NowPlayingBar? = null,
)

/** The menu holding an item opens: its [actions] under its [title]. */
data class ItemMenu(val title: String, val actions: List<ItemAction>)

data class ItemAction(val label: String, val run: () -> Unit)

data class NowPlayingBar(val title: String, val coverUrl: String?, val isPlaying: Boolean)

/** What the library screen can do. */
data class LibraryActions(
    val onUp: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onSort: (SortOption) -> Unit = {},
    val onDownloadedOnly: (Boolean) -> Unit = {},
    val onShowHidden: (Boolean) -> Unit = {},
    val onSettings: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onOpen: (LibraryItem) -> Unit = {},
    val onLongPress: (LibraryItem) -> Unit = {},
    val onDismissMenu: () -> Unit = {},
    val onOpenPlayer: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onStop: () -> Unit = {},
    /** The cover for an item's image id; none, and the row shows its placeholder. */
    val coverUrl: (UUID) -> String? = { null },
)

@Composable
fun LibraryScreen(state: LibraryUiState, actions: LibraryActions) {
    var searching by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }
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
            )
            if (searching) {
                SearchField(
                    query = state.query,
                    onQueryChange = actions.onQueryChange,
                    placeholder = stringResource(R.string.search_hint),
                    modifier = Modifier.testTag(VaultTestTags.SEARCH_FIELD),
                )
            }
            Box(modifier = Modifier.weight(1f)) { LibraryContent(state, actions) }
            state.nowPlaying?.let { MiniPlayer(it, actions) }
        }
    }
    state.itemMenu?.let { ItemMenuDialog(it, actions.onDismissMenu) }
}

@Composable
private fun LibraryTopBar(
    state: LibraryUiState,
    actions: LibraryActions,
    searching: Boolean,
    onSearch: () -> Unit,
    onCloseSearch: () -> Unit,
) {
    VaultTopBar(
        title = state.title,
        navigationIcon = if (state.canGoUp) {
            {
                IconButton(onClick = actions.onUp) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(CommonR.string.vault_back),
                    )
                }
            }
        } else {
            null
        },
        actions = {
            if (searching) {
                IconButton(onClick = onCloseSearch) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close_search))
                }
            } else {
                IconButton(onClick = onSearch) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_hint))
                }
            }
            LibraryMenu(state, actions)
        },
    )
}

@Composable
private fun LibraryMenu(state: LibraryUiState, actions: LibraryActions) {
    var open by remember { mutableStateOf(false) }
    var sorting by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(CommonR.string.vault_more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                onClick = {
                    open = false
                    actions.onSettings()
                },
            ) { Text(stringResource(R.string.activity_name_settings)) }
            CheckedMenuItem(stringResource(R.string.filter_downloaded_only), state.downloadedOnly) {
                open = false
                actions.onDownloadedOnly(!state.downloadedOnly)
            }
            CheckedMenuItem(stringResource(R.string.show_hidden_items), state.showHidden) {
                open = false
                actions.onShowHidden(!state.showHidden)
            }
            DropdownMenuItem(
                onClick = {
                    open = false
                    sorting = true
                },
            ) { Text(stringResource(R.string.library_sort_by)) }
        }
    }
    if (sorting) {
        ChoiceDialog(
            title = stringResource(R.string.library_sort_by),
            choices = listOf(
                Choice(SortOption.TITLE, stringResource(R.string.library_sort_title)),
                Choice(SortOption.AUTHOR, stringResource(R.string.library_sort_author)),
                Choice(SortOption.DATE_ADDED, stringResource(R.string.library_sort_date_added)),
                Choice(SortOption.LAST_PLAYED, stringResource(R.string.library_sort_last_played)),
            ),
            selected = state.sort,
            onSelect = actions.onSort,
            onDismiss = { sorting = false },
        )
    }
}

/** A menu item that switches something on or off; the item is what toggles, so it says whether it's on. */
@Composable
private fun CheckedMenuItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        onClick = onClick,
        modifier = Modifier.semantics { toggleableState = ToggleableState(checked) },
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun LibraryContent(state: LibraryUiState, actions: LibraryActions) {
    val refresh = rememberPullRefreshState(
        refreshing = state.loading && state.items.isNotEmpty(),
        onRefresh = actions.onRefresh,
    )
    Box(modifier = Modifier.fillMaxSize().pullRefresh(refresh)) {
        when {
            state.items.isEmpty() && state.loading -> LoadingState()
            state.items.isEmpty() && state.error != null -> ErrorState(state.error, onRetry = actions.onRefresh)
            state.items.isEmpty() -> EmptyState(stringResource(R.string.library_empty), icon = Icons.Filled.Headphones)
            else -> LibraryList(state.items, state.downloadedIds, state.hiddenIds, actions)
        }
        PullRefreshIndicator(
            refreshing = state.loading && state.items.isNotEmpty(),
            state = refresh,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * The items, each row given only whether it's downloaded and hidden: a download finishing then
 * redraws just its own row (with the whole id set, every row would redraw, as the list adapter
 * once did, reloading every cover). [row] draws one row; tests swap it to count redraws.
 */
@Composable
internal fun LibraryList(
    items: List<LibraryItem>,
    downloadedIds: Set<String>,
    hiddenIds: Set<String>,
    actions: LibraryActions,
    row: @Composable (item: LibraryItem, downloaded: Boolean, hidden: Boolean) -> Unit = { item, downloaded, hidden ->
        LibraryRow(item, downloaded, hidden, actions)
    },
) {
    val listState = rememberLazyListState()
    // The list holds its place by the item on top, so a new first item (an un-hidden book, a new
    // sort) would land just above the screen; at the top, stay at the top so it's seen
    LaunchedEffect(items.firstOrNull()?.id) {
        if (listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag(VaultTestTags.LIBRARY_LIST)) {
        items(items, key = { it.id }) { item ->
            row(item, item.isDownloaded(downloadedIds), item.id.toString() in hiddenIds)
        }
    }
}

@Composable
private fun LibraryRow(item: LibraryItem, downloaded: Boolean, hidden: Boolean, actions: LibraryActions) {
    val progress = item.progressPercent?.takeIf { it > 0f }
    val runtime = item.runTimeTicks?.let { ticks ->
        val length = formatRuntime(ticks)
        if (progress != null) stringResource(R.string.runtime_with_progress, length, progress.roundToInt()) else length
    }
    MediaListRow(
        title = item.displayName,
        subtitle = listOfNotNull(item.description.takeIf { it.isNotBlank() }, runtime).joinToString(" · ")
            .takeIf { it.isNotEmpty() },
        cover = {
            CoverArt(
                url = item.imageItemId?.let(actions.coverUrl),
                placeholder = if (item is LibraryItem.BrowseFolder) Icons.Filled.Folder else Icons.Filled.Headphones,
            )
        },
        progress = progress?.let { it / PERCENT },
        trailing = {
            if (downloaded) DownloadedMark(Modifier.testTag(VaultTestTags.DOWNLOADED_MARK))
            if (hidden) {
                Icon(Icons.Filled.VisibilityOff, contentDescription = stringResource(R.string.hidden_item_desc))
            }
        },
        modifier = Modifier
            .testTag(VaultTestTags.LIBRARY_ITEM)
            // Shown only with "show hidden items" on, so dimmed to read as hidden-but-visible
            .alpha(if (hidden) HIDDEN_ITEM_ALPHA else 1f)
            .combinedClickable(onClick = { actions.onOpen(item) }, onLongClick = { actions.onLongPress(item) }),
    )
}

@Composable
private fun ItemMenuDialog(menu: ItemMenu, onDismiss: () -> Unit) {
    ListDialog(title = menu.title, onDismiss = onDismiss) {
        menu.actions.forEach { action ->
            SettingsActionRow(
                label = action.label,
                onClick = {
                    onDismiss()
                    action.run()
                },
            )
        }
    }
}

@Composable
private fun MiniPlayer(nowPlaying: NowPlayingBar, actions: LibraryActions) {
    MiniPlayerBar(
        title = nowPlaying.title,
        subtitle = null,
        cover = { CoverArt(url = nowPlaying.coverUrl, placeholder = Icons.Filled.Headphones) },
        isPlaying = nowPlaying.isPlaying,
        onTogglePlayPause = actions.onPlayPause,
        onExpand = actions.onOpenPlayer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(onClick = actions.onStop) {
            Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.notification_action_stop))
        }
    }
}

internal fun LibraryItem.isDownloaded(downloadedIds: Set<String>): Boolean = when (this) {
    is LibraryItem.AudiobookBundle -> trackIds.any { it.toString() in downloadedIds }
    is LibraryItem.AudioFile -> id.toString() in downloadedIds
    else -> false
}

internal fun formatRuntime(ticks: Long): String {
    val totalSeconds = ticks / TICKS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${totalSeconds}s"
    }
}

private const val PERCENT = 100f
private const val HIDDEN_ITEM_ALPHA = 0.5f
private const val TICKS_PER_SECOND = 10_000_000L
private const val SECONDS_PER_HOUR = 3600L
private const val SECONDS_PER_MINUTE = 60L
