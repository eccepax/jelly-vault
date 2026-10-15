package org.jellyfin.mobile.music.nowplaying

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.common.ui.components.VaultTopBar
import org.jellyfin.mobile.common.ui.theme.VaultAlpha
import org.jellyfin.mobile.music.R
import org.jellyfin.mobile.music.library.AlbumCover
import org.jellyfin.mobile.music.library.NowPlayingIndicator
import org.jellyfin.mobile.music.ui.artistOrUnknown
import org.koin.compose.viewmodel.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun QueueScreen(
    state: QueueUiState,
    onBack: () -> Unit,
    onSkipTo: (index: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.systemBarsPadding()) {
            VaultTopBar(
                title = stringResource(R.string.queue_title),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
            QueueList(state, onSkipTo, onRemove, onMove)
        }
    }
}

/**
 * While dragging, rows reorder in a local copy so the drag tracks the finger; the one resulting move
 * is sent when the drag ends (round-tripping each step through the player would make rows jump).
 */
@Composable
private fun QueueList(
    state: QueueUiState,
    onSkipTo: (index: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    var rows by remember(state.upNext) { mutableStateOf(state.upNext) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromAt = rows.indexOfFirst { it.key == from.key }
        val toAt = rows.indexOfFirst { it.key == to.key }
        if (fromAt >= 0 && toAt >= 0) rows = rows.toMutableList().apply { add(toAt, removeAt(fromAt)) }
    }
    val firstUpNextIndex = (state.nowPlaying?.index ?: -1) + 1
    LazyColumn(state = listState) {
        state.nowPlaying?.let { row ->
            item(key = "header:now") { Header(R.string.now_playing) }
            item(key = "now") { QueueTrack(row = row, isCurrent = true, onClick = {}) }
        }
        item(key = "header:next") { Header(R.string.up_next) }
        if (rows.isEmpty()) {
            item(key = "empty") {
                Text(stringResource(R.string.nothing_up_next), modifier = Modifier.padding(16.dp))
            }
        }
        items(rows, key = { it.key }) { row ->
            ReorderableItem(reorderState, key = row.key) {
                val position = rows.indexOf(row)
                QueueTrack(
                    row = row,
                    isCurrent = false,
                    onClick = { onSkipTo(row.index) },
                    onRemove = { onRemove(row.index) },
                    onMoveUp = if (position > 0) ({ onMove(row.index, row.index - 1) }) else null,
                    onMoveDown = if (position < rows.lastIndex) ({ onMove(row.index, row.index + 1) }) else null,
                    handle = Modifier.draggableHandle(
                        onDragStopped = {
                            val to = firstUpNextIndex + rows.indexOf(row)
                            if (to != row.index) onMove(row.index, to)
                        },
                    ),
                )
            }
        }
    }
}

private val QueueRow.key: String get() = "up:$index"

@Composable
private fun Header(text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.subtitle1,
        color = MaterialTheme.colors.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun QueueTrack(
    row: QueueRow,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    handle: Modifier = Modifier,
) {
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                customActions = listOfNotNull(
                    onMoveUp?.let { move -> CustomAccessibilityAction(moveUp) { move().let { true } } },
                    onMoveDown?.let { move -> CustomAccessibilityAction(moveDown) { move().let { true } } },
                )
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AlbumCover(coverUrl = row.track.coverUrl, modifier = Modifier.size(44.dp))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = row.track.title,
                style = MaterialTheme.typography.body1,
                color = if (isCurrent) MaterialTheme.colors.primary else MaterialTheme.colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artistOrUnknown(row.track.artist),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onBackground.copy(alpha = VaultAlpha.SECONDARY),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) NowPlayingIndicator()
        onRemove?.let {
            IconButton(onClick = it) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.queue_remove, row.track.title))
            }
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = stringResource(R.string.reorder),
                modifier = handle.padding(8.dp),
            )
        }
    }
}

@Composable
fun QueueRoute(onBack: () -> Unit, viewModel: QueueViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    QueueScreen(
        state = state,
        onBack = onBack,
        onSkipTo = viewModel::skipTo,
        onRemove = viewModel::remove,
        onMove = viewModel::move,
    )
}
