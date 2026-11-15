package org.jellyfin.mobile.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.R
import org.jellyfin.mobile.common.ui.components.Choice
import org.jellyfin.mobile.common.ui.components.ChoiceDialog

/** How to sort the library, the [sort] it's in selected. */
@Composable
internal fun SortDialog(sort: SortOption, onSort: (SortOption) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(
        title = stringResource(R.string.library_sort_by),
        choices = listOf(
            Choice(SortOption.TITLE, stringResource(R.string.library_sort_title)),
            Choice(SortOption.AUTHOR, stringResource(R.string.library_sort_author)),
            Choice(SortOption.DATE_ADDED, stringResource(R.string.library_sort_date_added)),
            Choice(SortOption.LAST_PLAYED, stringResource(R.string.library_sort_last_played)),
        ),
        selected = sort,
        onSelect = onSort,
        onDismiss = onDismiss,
    )
}

/** How to group the whole library's books, the way it's [groupBy] selected. */
@Composable
internal fun GroupDialog(groupBy: GroupBy, onGroupBy: (GroupBy) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(
        title = stringResource(R.string.library_group_by),
        choices = listOf(
            Choice(GroupBy.NONE, stringResource(R.string.library_group_none)),
            Choice(GroupBy.AUTHOR, stringResource(R.string.library_group_author)),
            Choice(GroupBy.SERIES, stringResource(R.string.library_group_series)),
        ),
        selected = groupBy,
        onSelect = onGroupBy,
        onDismiss = onDismiss,
    )
}
