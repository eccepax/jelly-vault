package org.jellyfin.mobile.books.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.books.data.dao.BookProgressDao
import org.jellyfin.mobile.books.downloads.BookDownloadManager
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.data.dao.DownloadRecordDao
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber

enum class BookSortOption { TITLE, AUTHOR, DATE_ADDED }
enum class ProgressFilter { ALL, UNREAD, IN_PROGRESS, FINISHED }

class BookLibraryViewModel(
    private val apiClient: ApiClient,
    private val appPreferences: AppPreferences,
    private val bookProgressDao: BookProgressDao,
    private val bookDownloadManager: BookDownloadManager,
    private val downloadRecordDao: DownloadRecordDao,
    private val libraryId: String?,
) : ViewModel() {

    private val _allBooks = MutableStateFlow<List<BaseItemDto>>(emptyList())
    private val _loading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    val sortOption = MutableStateFlow(BookSortOption.TITLE)
    val progressFilter = MutableStateFlow(ProgressFilter.ALL)
    val tagFilter = MutableStateFlow<String?>(null)
    val showDownloadedOnly = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")

    private val _downloadedIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedIds: StateFlow<Set<String>> get() = _downloadedIds

    private val _filteredBooks = MutableStateFlow<List<BaseItemDto>>(emptyList())
    val filteredBooks: StateFlow<List<BaseItemDto>> get() = _filteredBooks

    val loading: StateFlow<Boolean> get() = _loading
    val error: StateFlow<String?> get() = _error

    init {
        viewModelScope.launch {
            downloadRecordDao.getAll()
                .map { records -> records.map { it.itemId }.toSet() }
                .collect { ids -> _downloadedIds.value = ids }
        }
        viewModelScope.launch {
            combine(
                combine(_allBooks, sortOption, progressFilter, tagFilter) { books, sort, progress, tag ->
                    applyFiltersAndSort(books, sort, progress, tag)
                },
                showDownloadedOnly,
                _downloadedIds,
                searchQuery,
            ) { sorted, downloadedOnly, downloadedSet, query ->
                val afterDownloadFilter = if (downloadedOnly) sorted.filter { it.id?.toString() in downloadedSet } else sorted
                if (query.isBlank()) {
                    afterDownloadFilter
                } else {
                    val q = query.trim().lowercase()
                    afterDownloadFilter.filter { book ->
                        book.name?.lowercase()?.contains(q) == true ||
                            book.albumArtist?.lowercase()?.contains(q) == true ||
                            book.seriesName?.lowercase()?.contains(q) == true
                    }
                }
            }.collect { filtered ->
                _filteredBooks.value = filtered
            }
        }
        loadBooks()
    }

    fun loadBooks() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val serverId = appPreferences.currentServerId
                val userId = appPreferences.currentUserId
                if (serverId == null || userId == null) {
                    _error.value = "Not connected to a server"
                    return@launch
                }

                val sortField = when (sortOption.value) {
                    BookSortOption.TITLE -> ItemSortBy.SORT_NAME
                    BookSortOption.AUTHOR -> ItemSortBy.ALBUM_ARTIST
                    BookSortOption.DATE_ADDED -> ItemSortBy.DATE_CREATED
                }

                val result = withContext(Dispatchers.IO) {
                    apiClient.itemsApi.getItems(
                        includeItemTypes = listOf(BaseItemKind.BOOK),
                        sortBy = listOf(sortField),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        recursive = true,
                        fields = emptyList(),
                        parentId = libraryId?.let {
                            runCatching { java.util.UUID.fromString(it) }.getOrNull()
                        },
                    ).content
                }
                _allBooks.value = result.items.orEmpty()
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to load books")
                _error.value = e.message ?: "Failed to load books"
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * [BookDownloadManager.downloadBook] can throw [java.io.IOException] (network/file I/O) or
     * [IllegalStateException] (a non-2xx response) — both are just "this download attempt failed,"
     * logged so the failure doesn't crash the screen.
     */
    @Suppress("TooGenericExceptionCaught")
    fun downloadBook(book: BaseItemDto) {
        viewModelScope.launch {
            try {
                bookDownloadManager.downloadBook(book)
            } catch (e: Exception) {
                Timber.e(e, "Failed to download book ${book.name}")
            }
        }
    }

    fun deleteDownload(book: BaseItemDto) {
        viewModelScope.launch {
            book.id?.toString()?.let { bookDownloadManager.deleteDownload(it) }
        }
    }

    private suspend fun applyFiltersAndSort(
        books: List<BaseItemDto>,
        sort: BookSortOption,
        progress: ProgressFilter,
        tag: String?,
    ): List<BaseItemDto> {
        val allProgress = bookProgressDao.getAllProgress().associateBy { it.bookItemId }
        return filterAndSortBooks(books, allProgress, sort, progress, tag)
    }

    fun getAvailableTags(): List<String> = availableTags(_allBooks.value)
}
