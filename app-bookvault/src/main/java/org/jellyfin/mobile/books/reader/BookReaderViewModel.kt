package org.jellyfin.mobile.books.reader

import android.app.Application
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jellyfin.mobile.books.data.dao.BookBookmarkDao
import org.jellyfin.mobile.books.data.entity.BookBookmarkEntity
import org.jellyfin.mobile.books.downloads.BookFiles
import org.jellyfin.mobile.books.progress.BookProgressSync
import timber.log.Timber
import java.io.File
import java.io.IOException

enum class BookType { PDF, EPUB, UNKNOWN }

class BookReaderViewModel(
    app: Application,
    private val progressSync: BookProgressSync,
    private val bookFiles: BookFiles,
    private val bookBookmarkDao: BookBookmarkDao,
) : AndroidViewModel(app) {

    private val pdfCore = PdfRendererCore()
    private val epubRenderer = EpubRenderer(app)

    private val _bookType = MutableStateFlow(BookType.UNKNOWN)
    val bookType: StateFlow<BookType> get() = _bookType

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> get() = _currentPage

    /**
     * The contents entry last chosen, while its chapter's file is open: several chapters can share
     * a file, and this says which of them is being read (and where in the file to open it).
     */
    private val _chosenTocEntry = MutableStateFlow<EpubTocEntry?>(null)
    val chosenTocEntry: StateFlow<EpubTocEntry?> get() = _chosenTocEntry

    /**
     * Each time an entry is chosen from the contents, even the one already being read (to go back to
     * where it starts): the chapter is opened there again. Not replayed, so coming back to the
     * reader leaves the chapter where it was scrolled to.
     */
    private val _tocJumps = MutableSharedFlow<EpubTocEntry>(extraBufferCapacity = 1)
    val tocJumps: SharedFlow<EpubTocEntry> get() = _tocJumps

    private val _totalPages = MutableStateFlow(0)
    val totalPages: StateFlow<Int> get() = _totalPages

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> get() = _loading

    private val _nightMode = MutableStateFlow(false)
    val nightMode: StateFlow<Boolean> get() = _nightMode

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> get() = _error

    private val _bookmarks = MutableStateFlow<List<BookBookmarkEntity>>(emptyList())
    val bookmarks: StateFlow<List<BookBookmarkEntity>> get() = _bookmarks

    private var currentBookItemId: String? = null

    val targetWidth: Int by lazy {
        val wm = app.getSystemService<WindowManager>()!!
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)
        metrics.widthPixels
    }

    /**
     * The try block spans a network download, file-format sniffing, and two independent renderers
     * (PDF, EPUB) opening a real file — each can fail with a different exception type. Any failure
     * here means "couldn't open this book," reported the same way regardless of cause.
     */
    @Suppress("TooGenericExceptionCaught")
    fun loadBook(bookItemId: String, initialNightMode: Boolean) {
        val sameBook = bookItemId == currentBookItemId
        // The reader asks again each time it's recreated (rotation): the book is already open,
        // as the reader left it, night mode and all. Only a book that failed to open is tried again.
        if (sameBook && _error.value == null) return
        if (!sameBook) {
            currentBookItemId = bookItemId
            _nightMode.value = initialNightMode
            viewModelScope.launch {
                bookBookmarkDao.getForBook(bookItemId).collect { bookmarks -> _bookmarks.value = bookmarks }
            }
        }
        // Loading from the moment it's asked for, so no one reads the last attempt's error in between
        _loading.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                val cachedFile = bookFiles.fileFor(bookItemId)
                val type = detectBookType(cachedFile)
                // Populate totalPages/currentPage BEFORE publishing bookType — the reader's
                // bookType collector builds the pager adapter as soon as it sees the new type, and
                // nothing re-triggers that build later, so a premature emission leaves it stuck
                // showing zero pages (a blank screen) until the reader mode is manually toggled.
                when (type) {
                    BookType.PDF -> {
                        pdfCore.openFile(cachedFile)
                        _totalPages.value = pdfCore.pageCount
                    }
                    BookType.EPUB -> {
                        epubRenderer.openFile(cachedFile, bookItemId)
                        _totalPages.value = epubRenderer.chapterCount
                    }
                    BookType.UNKNOWN -> throw IOException("Unsupported book format")
                }
                // Where it was left, here or on another device
                _currentPage.value = progressSync.startPage(bookItemId, _totalPages.value)
                _bookType.value = type
            } catch (e: Exception) {
                Timber.e(e, "Failed to load book $bookItemId")
                _error.value = e.message ?: "Failed to open book"
            } finally {
                _loading.value = false
            }
        }
    }

    suspend fun renderPage(pageIndex: Int): android.graphics.Bitmap? = runCatching {
        pdfCore.renderPage(pageIndex, targetWidth, _nightMode.value)
    }.getOrNull()

    fun getChapterFile(index: Int): File? = epubRenderer.getChapterFile(index)

    fun getBookDir(): File? = epubRenderer.bookDir

    /** Real chapter titles for the currently-loaded EPUB, or empty for PDF/before loading. */
    fun getTableOfContents(): List<EpubTocEntry> = epubRenderer.getTableOfContents()

    /** The title of the chapter being read in the file at [chapterIndex]: the one chosen from the contents, if it's there. */
    fun getChapterTitle(chapterIndex: Int): String? =
        _chosenTocEntry.value?.takeIf { it.spineIndex == chapterIndex }?.title ?: epubRenderer.chapterTitleAt(chapterIndex)

    /** Goes to [entry]'s chapter, at the place in its file where it starts. */
    fun openTocEntry(entry: EpubTocEntry) {
        _chosenTocEntry.value = entry
        setPage(entry.spineIndex)
        _tocJumps.tryEmit(entry)
    }

    fun setPage(pageIndex: Int) {
        val total = _totalPages.value
        val clampedIndex = if (total > 0) pageIndex.coerceIn(0, total - 1) else pageIndex
        // A chapter chosen from the contents is left behind with its file
        if (_chosenTocEntry.value?.spineIndex != clampedIndex) _chosenTocEntry.value = null
        if (clampedIndex == _currentPage.value) return
        _currentPage.value = clampedIndex
        val itemId = currentBookItemId ?: return
        // Saved where leaving the book can't cut it off, ahead of the send that leaving starts
        progressSync.save(itemId, clampedIndex, total)
    }

    /** Bookmarks the current position. Auto-labels from the chapter title (EPUB) or page number (PDF). */
    fun addBookmark(note: String? = null) {
        val itemId = currentBookItemId ?: return
        val page = _currentPage.value
        viewModelScope.launch {
            bookBookmarkDao.insert(
                BookBookmarkEntity(
                    bookItemId = itemId,
                    pageIndex = page,
                    label = bookmarkLabel(page),
                    note = note?.trim()?.takeIf { it.isNotEmpty() },
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun bookmarkLabel(page: Int): String =
        _chosenTocEntry.value?.takeIf { it.spineIndex == page }?.title
            ?: epubRenderer.getTableOfContents().firstOrNull { it.spineIndex == page }?.title
            ?: "Page ${page + 1}"

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { bookBookmarkDao.delete(id) }
    }

    fun jumpToBookmark(bookmark: BookBookmarkEntity) {
        setPage(bookmark.pageIndex)
    }

    fun toggleNightMode() {
        _nightMode.value = !_nightMode.value
        if (_bookType.value == BookType.PDF) {
            pdfCore.invalidateCache()
        }
    }

    /** Sends the server the place in the book, so it opens there on the reader's other devices. */
    fun sendProgress() {
        if (currentBookItemId != null) progressSync.sendSoon()
    }

    internal fun detectBookType(file: File): BookType {
        if (!file.exists() || file.length() < SIGNATURE_HEADER_BYTES) return BookType.UNKNOWN
        val header = ByteArray(SIGNATURE_HEADER_BYTES)
        file.inputStream().use { it.read(header) }
        return when {
            header[0] == PDF_MAGIC_BYTE_0 && header[1] == PDF_MAGIC_BYTE_1 -> BookType.PDF // %PDF
            header[0] == EPUB_MAGIC_BYTE_0 && header[1] == EPUB_MAGIC_BYTE_1 -> BookType.EPUB // PK (ZIP)
            else -> BookType.UNKNOWN
        }
    }

    override fun onCleared() {
        super.onCleared()
        // The book's closed: its place goes to the server
        sendProgress()
        pdfCore.close()
        epubRenderer.close()
    }

    private companion object {
        // "%P" — start of the "%PDF" file signature.
        const val PDF_MAGIC_BYTE_0: Byte = 0x25
        const val PDF_MAGIC_BYTE_1: Byte = 0x50

        // "PK" — ZIP local file header signature (EPUB is a ZIP container).
        const val EPUB_MAGIC_BYTE_0: Byte = 0x50
        const val EPUB_MAGIC_BYTE_1: Byte = 0x4B

        const val SIGNATURE_HEADER_BYTES = 4
    }
}
