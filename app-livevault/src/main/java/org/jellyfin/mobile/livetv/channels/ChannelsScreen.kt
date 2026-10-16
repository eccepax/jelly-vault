package org.jellyfin.mobile.livetv.channels

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import org.jellyfin.mobile.common.ui.ScrollToTopOnChange
import org.jellyfin.mobile.common.ui.components.ConfirmDialog
import org.jellyfin.mobile.common.ui.components.EmptyState
import org.jellyfin.mobile.common.ui.components.ErrorState
import org.jellyfin.mobile.common.ui.components.LoadingState
import org.jellyfin.mobile.common.ui.components.SearchField
import org.jellyfin.mobile.common.ui.dpadLeavesTextField
import org.jellyfin.mobile.common.ui.theme.VaultAlpha
import org.jellyfin.mobile.common.ui.theme.VaultColors
import org.jellyfin.mobile.common.ui.theme.focusBorder
import org.jellyfin.mobile.livetv.R
import org.jellyfin.mobile.livetv.catalog.ChannelFilter
import org.jellyfin.mobile.livetv.domain.LiveChannel
import org.koin.compose.viewmodel.koinViewModel
import java.time.Instant

/**
 * Every channel, narrowed by a tag chip (or Favorites) and a search. One column on phones, a grid
 * on tablets and TVs; cards and chips are focusable, so a TV remote's D-pad moves through them.
 * [onTv], the remote starts on the channel to continue (else the first one) rather than on search.
 */
@Composable
fun ChannelsScreen(
    state: ChannelsUiState,
    now: Instant,
    onTv: Boolean = false,
    actions: ChannelsActions,
) {
    val startFocus = remember { FocusRequester() }
    val loaded = state is ChannelsUiState.Loaded && state.channels.isNotEmpty()
    LaunchedEffect(onTv, loaded) {
        if (onTv && loaded) startFocus.requestFocus()
    }
    Surface(color = MaterialTheme.colors.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding()) {
            Header(
                query = (state as? ChannelsUiState.Loaded)?.filter?.query.orEmpty(),
                onQueryChange = actions.onQueryChange,
                onSignOut = actions.onSignOut,
            )
            when (state) {
                ChannelsUiState.Loading -> LoadingState(Modifier.testTag(ChannelsScreenTags.LOADING))
                ChannelsUiState.Error -> ErrorState(stringResource(R.string.channels_error), onRetry = actions.onRetry)
                is ChannelsUiState.Loaded -> {
                    state.lastChannel?.let { channel ->
                        ContinueWatching(channel, now, actions.onFavoriteToggle, actions.onChannelClick, startFocus)
                    }
                    FilterChips(
                        tags = state.tags,
                        filter = state.filter,
                        onTagSelect = actions.onTagSelect,
                        onFavoritesSelect = actions.onFavoritesSelect,
                    )
                    if (state.channels.isEmpty()) {
                        EmptyState(emptyMessage(state.filter))
                    } else {
                        ChannelGrid(
                            channels = state.channels,
                            now = now,
                            onFavoriteToggle = actions.onFavoriteToggle,
                            onChannelClick = actions.onChannelClick,
                            firstFocus = startFocus.takeIf { state.lastChannel == null },
                            // Another tag, or favorites, is another list: start it at its top
                            scrollKey = "${state.filter.tag}|${state.filter.favoritesOnly}",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(query: String, onQueryChange: (String) -> Unit, onSignOut: () -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                stringResource(R.string.live_tv_title),
                style = MaterialTheme.typography.h5,
                modifier = Modifier.weight(1f),
            )
            MoreMenu(onSignOut)
        }
        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(R.string.channels_search),
            modifier = Modifier
                .testTag(ChannelsScreenTags.SEARCH)
                .dpadLeavesTextField(),
        )
    }
}

@Composable
private fun MoreMenu(onSignOut: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    if (confirming) {
        ConfirmDialog(
            message = stringResource(R.string.sign_out_question),
            confirmLabel = stringResource(R.string.sign_out_confirm),
            onConfirm = onSignOut,
            onDismiss = { confirming = false },
        )
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.channels_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                onClick = {
                    expanded = false
                    confirming = true
                },
            ) {
                Text(stringResource(R.string.sign_out))
            }
        }
    }
}

@Composable
private fun FilterChips(
    tags: List<String>,
    filter: ChannelFilter,
    onTagSelect: (String?) -> Unit,
    onFavoritesSelect: () -> Unit,
) {
    LazyRow(
        modifier = Modifier.testTag(ChannelsScreenTags.CHIPS),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Chip(
                label = stringResource(R.string.channels_all),
                selected = filter.tag == null && !filter.favoritesOnly,
                onClick = { onTagSelect(null) },
            )
        }
        item {
            Chip(
                label = stringResource(R.string.channels_favorites),
                selected = filter.favoritesOnly,
                onClick = onFavoritesSelect,
            )
        }
        items(tags, key = { it }) { tag ->
            Chip(label = tag, selected = filter.tag == tag, onClick = { onTagSelect(tag) })
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val colors = MaterialTheme.colors
    Text(
        text = label,
        color = if (selected) colors.onPrimary else colors.onSurface,
        style = MaterialTheme.typography.body2,
        modifier = Modifier
            .clip(ChipShape)
            .background(if (selected) colors.primary else colors.surface)
            .focusBorder(focused, ChipShape)
            .selectable(
                selected = selected,
                interactionSource = interactions,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ChannelGrid(
    channels: List<LiveChannel>,
    now: Instant,
    onFavoriteToggle: (LiveChannel) -> Unit,
    onChannelClick: (LiveChannel) -> Unit,
    /** Given to the first card, when it's where the remote starts. */
    firstFocus: FocusRequester?,
    scrollKey: String,
) {
    val grid = rememberLazyGridState()
    ScrollToTopOnChange(scrollKey) { grid.scrollToItem(0) }
    LazyVerticalGrid(
        state = grid,
        modifier = Modifier.testTag(ChannelsScreenTags.GRID),
        columns = GridCells.Adaptive(minSize = CARD_MIN_WIDTH),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(channels, key = { it.id }) { channel ->
            ChannelCard(
                channel = channel,
                now = now,
                onClick = { onChannelClick(channel) },
                onFavoriteToggle = { onFavoriteToggle(channel) },
                focus = firstFocus.takeIf { channel == channels.first() },
            )
        }
    }
}

@Composable
private fun ContinueWatching(
    channel: LiveChannel,
    now: Instant,
    onFavoriteToggle: (LiveChannel) -> Unit,
    onChannelClick: (LiveChannel) -> Unit,
    focus: FocusRequester,
) {
    Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp)) {
        Text(
            stringResource(R.string.channels_continue_watching),
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        ChannelCard(
            channel = channel,
            now = now,
            onClick = { onChannelClick(channel) },
            onFavoriteToggle = { onFavoriteToggle(channel) },
            tag = ChannelsScreenTags.CONTINUE,
            focus = focus,
        )
    }
}

@Composable
private fun ChannelCard(
    channel: LiveChannel,
    now: Instant,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    tag: String = ChannelsScreenTags.channel(channel.id),
    focus: FocusRequester? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .testTag(tag)
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colors.surface)
            .focusBorder(focused, CardShape)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .clickable(interactionSource = interactions, indication = null, onClick = onClick)
            .padding(10.dp),
    ) {
        ChannelLogo(channel.logoUrl)
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = channel.number,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = channel.displayName,
                    style = MaterialTheme.typography.subtitle1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NowPlaying(channel, now)
        }
        FavoriteButton(channel, onFavoriteToggle)
    }
}

@Composable
private fun NowPlaying(channel: LiveChannel, now: Instant) {
    val program = channel.nowPlaying
    val dim = MaterialTheme.colors.onSurface.copy(alpha = VaultAlpha.SECONDARY)
    if (program == null) {
        Text(stringResource(R.string.channels_no_guide), style = MaterialTheme.typography.body2, color = dim)
        return
    }
    Text(program.title, style = MaterialTheme.typography.body2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        LinearProgressIndicator(
            progress = program.progressAt(now),
            modifier = Modifier
                .width(64.dp)
                .padding(end = 8.dp),
        )
        Text(
            stringResource(R.string.channels_until, program.endTimeText()),
            style = MaterialTheme.typography.caption,
            color = dim,
        )
    }
}

@Composable
private fun ChannelLogo(logoUrl: String?) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(LOGO_SIZE)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.3f)),
    ) {
        Icon(
            Icons.Filled.LiveTv,
            contentDescription = null,
            tint = MaterialTheme.colors.onSurface.copy(alpha = VaultAlpha.SECONDARY),
        )
        if (logoUrl != null) {
            AsyncImage(
                model = logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
            )
        }
    }
}

@Composable
private fun FavoriteButton(channel: LiveChannel, onToggle: () -> Unit) {
    val description = stringResource(
        if (channel.isFavorite) R.string.channels_unfavorite else R.string.channels_favorite,
        channel.displayName,
    )
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (channel.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = description,
            tint = if (channel.isFavorite) {
                VaultColors.Favorite
            } else {
                MaterialTheme.colors.onSurface.copy(alpha = VaultAlpha.SECONDARY)
            },
        )
    }
}

@Composable
private fun emptyMessage(filter: ChannelFilter): String = when {
    filter.query.isNotBlank() -> stringResource(R.string.channels_no_match, filter.query.trim())
    filter.favoritesOnly -> stringResource(R.string.channels_no_favorites)
    else -> stringResource(R.string.channels_empty)
}

private val CARD_MIN_WIDTH = 340.dp
private val LOGO_SIZE = 56.dp
private val ChipShape = CircleShape
private val CardShape = RoundedCornerShape(8.dp)

/** The channel browser, refreshing what's on now when the app returns and when a program ends. */
@Composable
fun ChannelsRoute(
    onChannelClick: (channel: LiveChannel, lineup: List<LiveChannel>) -> Unit,
    onSignOut: () -> Unit,
    viewModel: ChannelsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val now by produceState(Instant.now()) {
        while (true) {
            delay(CLOCK_TICK_MS)
            value = Instant.now()
        }
    }
    LaunchedEffect(now) { viewModel.refreshIfEnded(now) }
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    val onTv = LocalConfiguration.current.uiMode and Configuration.UI_MODE_TYPE_MASK ==
        Configuration.UI_MODE_TYPE_TELEVISION
    ChannelsScreen(
        state = state,
        now = now,
        onTv = onTv,
        actions = ChannelsActions(
            onQueryChange = viewModel::onQueryChange,
            onTagSelect = viewModel::selectTag,
            onFavoritesSelect = viewModel::showFavorites,
            onFavoriteToggle = { viewModel.toggleFavorite(it.id) },
            // Zapping from the player moves through the channels the viewer was looking at.
            onChannelClick = { channel ->
                onChannelClick(
                    channel,
                    (state as? ChannelsUiState.Loaded)?.channels.orEmpty(),
                )
            },
            onRetry = viewModel::retry,
            onSignOut = onSignOut,
        ),
    )
}

private const val CLOCK_TICK_MS = 30_000L
