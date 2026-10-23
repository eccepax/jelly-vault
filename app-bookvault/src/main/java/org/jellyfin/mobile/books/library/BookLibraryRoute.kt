package org.jellyfin.mobile.books.library

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.app.LibraryViewMode
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The view the whole library opens in is saved under this. */
private const val VIEW_MODE_KEY = "global"

/** The library: every book, opened with a tap, downloaded or removed with a long press. */
@Composable
fun BookLibraryRoute(
    onOpenBook: (bookId: String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: BookLibraryViewModel = koinViewModel { parametersOf(null) },
    preferences: BookAppPreferences = koinInject(),
) {
    val context = LocalContext.current
    var viewMode by remember { mutableStateOf(preferences.getLibraryViewMode(VIEW_MODE_KEY)) }
    val actions = BookLibraryActions(
        onOpen = { book -> book.id?.let { onOpenBook(it.toString()) } },
        // A download needs no confirming, unlike removing one
        onDownload = { book ->
            viewModel.downloadBook(book)
            Toast.makeText(context, context.getString(R.string.downloading_book), Toast.LENGTH_SHORT).show()
        },
        onRemoveDownload = viewModel::deleteDownload,
        onToggleViewMode = {
            viewMode = if (viewMode == LibraryViewMode.LIST) LibraryViewMode.SHELF else LibraryViewMode.LIST
            preferences.setLibraryViewMode(VIEW_MODE_KEY, viewMode)
        },
        onQueryChange = { viewModel.searchQuery.value = it },
        onSort = { viewModel.sortOption.value = it },
        onProgressFilter = { viewModel.progressFilter.value = it },
        onTagFilter = { viewModel.tagFilter.value = it },
        onDownloadedOnly = { viewModel.showDownloadedOnly.value = it },
        onSettings = onOpenSettings,
        onRetry = viewModel::loadBooks,
    )

    val books by viewModel.filteredBooks.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()
    val downloadedIds by viewModel.downloadedIds.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val sort by viewModel.sortOption.collectAsState()
    val progressFilter by viewModel.progressFilter.collectAsState()
    val tagFilter by viewModel.tagFilter.collectAsState()
    val downloadedOnly by viewModel.showDownloadedOnly.collectAsState()
    // Tags come from every loaded book, so a new list of books may bring new ones
    val availableTags = remember(books) { viewModel.getAvailableTags() }
    BookLibraryScreen(
        BookLibraryUiState(
            books = books,
            loading = loading,
            error = error,
            downloadedIds = downloadedIds,
            viewMode = viewMode,
            query = query,
            sort = sort,
            progressFilter = progressFilter,
            tagFilter = tagFilter,
            availableTags = availableTags,
            downloadedOnly = downloadedOnly,
        ),
        actions,
    )
}
