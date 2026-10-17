package org.jellyfin.mobile.books.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.app.LibraryViewMode
import org.jellyfin.mobile.books.databinding.FragmentComposeBinding
import org.jellyfin.mobile.books.reader.BookReaderFragment
import org.jellyfin.mobile.books.settings.BookSettingsFragment
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class BookLibraryFragment : Fragment() {

    private val bookAppPreferences: BookAppPreferences by inject()
    private val libraryId: String? get() = arguments?.getString(ARG_LIBRARY_ID)
    private val viewModel: BookLibraryViewModel by viewModel { parametersOf(libraryId) }

    private var _binding: FragmentComposeBinding? = null
    private val binding get() = _binding!!

    private var viewMode by mutableStateOf(LibraryViewMode.LIST)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentComposeBinding.inflate(inflater, container, false)
        return binding.composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ViewCompat.requestApplyInsets(binding.composeView)
        viewMode = bookAppPreferences.getLibraryViewMode(viewModeKey)
        val actions = BookLibraryActions(
            onOpen = { book -> book.id?.let { openReader(it.toString()) } },
            onDownload = ::download,
            onRemoveDownload = viewModel::deleteDownload,
            onToggleViewMode = ::toggleViewMode,
            onQueryChange = { viewModel.searchQuery.value = it },
            onSort = { viewModel.sortOption.value = it },
            onProgressFilter = { viewModel.progressFilter.value = it },
            onTagFilter = { viewModel.tagFilter.value = it },
            onDownloadedOnly = { viewModel.showDownloadedOnly.value = it },
            onSettings = ::openSettings,
            onRetry = viewModel::loadBooks,
        )
        binding.composeView.setContent {
            AppTheme(VaultBrand.BookVault) {
                BookLibraryScreen(collectState(), actions)
            }
        }
    }

    @Composable
    private fun collectState(): BookLibraryUiState {
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
        return BookLibraryUiState(
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
        )
    }

    private val viewModeKey: String get() = libraryId ?: "global"

    private fun toggleViewMode() {
        viewMode = if (viewMode == LibraryViewMode.LIST) LibraryViewMode.SHELF else LibraryViewMode.LIST
        bookAppPreferences.setLibraryViewMode(viewModeKey, viewMode)
    }

    /** Starts downloading [book] at once; a download needs no confirming, unlike removing one. */
    private fun download(book: BaseItemDto) {
        viewModel.downloadBook(book)
        Toast.makeText(requireContext(), getString(R.string.downloading_book), Toast.LENGTH_SHORT).show()
    }

    private fun openReader(bookItemId: String) {
        val fragment = BookReaderFragment.newInstance(bookItemId)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun openSettings() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, BookSettingsFragment())
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_LIBRARY_ID = "library_id"

        fun newInstance(libraryId: String? = null) = BookLibraryFragment().apply {
            arguments = Bundle().apply {
                libraryId?.let { putString(ARG_LIBRARY_ID, it) }
            }
        }
    }
}
