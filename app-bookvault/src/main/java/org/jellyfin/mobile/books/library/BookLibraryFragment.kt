package org.jellyfin.mobile.books.library

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.app.LibraryViewMode
import org.jellyfin.mobile.books.databinding.FragmentLibraryBinding
import org.jellyfin.mobile.books.reader.BookReaderFragment
import org.jellyfin.mobile.books.settings.BookSettingsFragment
import org.jellyfin.mobile.common.ui.utils.padForNavigationBar
import org.jellyfin.mobile.common.ui.utils.padForStatusBar
import org.jellyfin.sdk.model.api.BaseItemDto
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class BookLibraryFragment : Fragment() {

    private val bookAppPreferences: BookAppPreferences by inject()
    private val libraryId: String? get() = arguments?.getString(ARG_LIBRARY_ID)
    private val viewModel: BookLibraryViewModel by viewModel { parametersOf(libraryId) }

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val listAdapter = BookListAdapter(
        onBookClick = { book -> book.id?.let { openReader(it.toString()) } },
        onBookLongClick = { book -> showDownloadDialog(book) },
    )
    private val shelfAdapter = BookShelfAdapter(
        onBookClick = { book -> book.id?.let { openReader(it.toString()) } },
        onBookLongClick = { book -> showDownloadDialog(book) },
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerViews()
        applyViewMode(bookAppPreferences.getLibraryViewMode(libraryId ?: "global"))
        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.padForStatusBar()
        binding.root.padForNavigationBar()

        binding.toolbar.setTitle(R.string.library)
        binding.toolbar.inflateMenu(R.menu.menu_library)
        setupSearchView()
        binding.toolbar.setOnMenuItemClickListener { item -> onMenuItemClick(item) }
    }

    private fun setupSearchView() {
        val searchItem = binding.toolbar.menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = getString(R.string.search_hint)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = true
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.searchQuery.value = newText.orEmpty()
                return true
            }
        })
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem) = true
            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                viewModel.searchQuery.value = ""
                return true
            }
        })
    }

    private fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_toggle_view -> {
            toggleViewMode()
            true
        }
        R.id.action_sort -> {
            showSortDialog()
            true
        }
        R.id.action_filter_progress -> {
            showProgressFilterDialog()
            true
        }
        R.id.action_filter_downloaded -> {
            viewModel.showDownloadedOnly.value = !viewModel.showDownloadedOnly.value
            item.isChecked = viewModel.showDownloadedOnly.value
            true
        }
        R.id.action_settings -> {
            openSettings()
            true
        }
        else -> false
    }

    private fun toggleViewMode() {
        val currentMode = bookAppPreferences.getLibraryViewMode(libraryId ?: "global")
        val newMode = if (currentMode == LibraryViewMode.LIST) LibraryViewMode.SHELF else LibraryViewMode.LIST
        bookAppPreferences.setLibraryViewMode(libraryId ?: "global", newMode)
        applyViewMode(newMode)
    }

    private fun setupRecyclerViews() {
        binding.recyclerViewList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = listAdapter
        }
        binding.recyclerViewShelf.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = shelfAdapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeFilteredBooks() }
                launch { observeLoading() }
                launch { observeError() }
                launch { observeDownloadedIds() }
                launch { observeShowDownloadedOnly() }
            }
        }
    }

    private suspend fun observeFilteredBooks() {
        viewModel.filteredBooks.collect { books ->
            listAdapter.submitList(books)
            shelfAdapter.submitList(books)
            binding.textEmpty.isVisible = books.isEmpty() && !viewModel.loading.value
        }
    }

    private suspend fun observeLoading() {
        viewModel.loading.collect { loading ->
            binding.progressBar.isVisible = loading
        }
    }

    private suspend fun observeError() {
        viewModel.error.collect { error ->
            if (error != null) {
                binding.textEmpty.text = error
                binding.textEmpty.isVisible = true
            }
        }
    }

    private suspend fun observeDownloadedIds() {
        viewModel.downloadedIds.collect { ids ->
            listAdapter.updateDownloadedIds(ids)
            shelfAdapter.updateDownloadedIds(ids)
        }
    }

    private suspend fun observeShowDownloadedOnly() {
        viewModel.showDownloadedOnly.collect { downloadedOnly ->
            binding.toolbar.menu.findItem(R.id.action_filter_downloaded)?.isChecked = downloadedOnly
        }
    }

    private fun applyViewMode(mode: LibraryViewMode) {
        binding.recyclerViewList.isVisible = mode == LibraryViewMode.LIST
        binding.recyclerViewShelf.isVisible = mode == LibraryViewMode.SHELF
    }

    internal fun showDownloadDialog(book: BaseItemDto) {
        val itemId = book.id?.toString() ?: return
        val isDownloaded = itemId in viewModel.downloadedIds.value
        if (isDownloaded) {
            AlertDialog.Builder(requireContext())
                .setTitle(book.name)
                .setMessage(getString(R.string.remove_download))
                .setPositiveButton(android.R.string.ok) { _, _ -> viewModel.deleteDownload(book) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } else {
            viewModel.downloadBook(book)
            Toast.makeText(requireContext(), getString(R.string.downloading_book), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.sort_title),
            getString(R.string.sort_author),
            getString(R.string.sort_date_added),
        )
        val currentIndex = viewModel.sortOption.value.ordinal
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.sort_by)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                viewModel.sortOption.value = BookSortOption.entries[which]
                dialog.dismiss()
            }
            .show()
    }

    private fun showProgressFilterDialog() {
        val options = arrayOf(
            getString(R.string.filter_all),
            getString(R.string.filter_unread),
            getString(R.string.filter_in_progress),
            getString(R.string.filter_finished),
        )
        val currentIndex = viewModel.progressFilter.value.ordinal
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.filter_progress)
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                viewModel.progressFilter.value = ProgressFilter.entries[which]
                dialog.dismiss()
            }
            .show()
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
