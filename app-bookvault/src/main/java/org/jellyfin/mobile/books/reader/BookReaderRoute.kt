package org.jellyfin.mobile.books.reader

import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** The book [bookId], opened where it was left; its place is sent to the server as the app is put away. */
@Composable
fun BookReaderRoute(
    bookId: String,
    onBack: () -> Unit,
    viewModel: BookReaderViewModel = koinViewModel(),
    appPreferences: BookAppPreferences = koinInject(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val nightMode = isSystemInDarkTheme()
    var scrollMode by rememberSaveable { mutableStateOf(true) }
    var textSettings by remember { mutableStateOf(appPreferences.readTextSettings()) }

    // Asked again on rotation: the view model already has the book open, as the reader left it
    LaunchedEffect(viewModel, bookId) { viewModel.loadBook(bookId, nightMode) }
    LaunchedEffect(viewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.error.collect { error ->
                if (error != null) Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.sendProgress() }

    /** Saves a text setting, then shows it: on the screen, and in the chapters drawn again with it. */
    fun changeTextSettings(save: () -> Unit) {
        save()
        textSettings = appPreferences.readTextSettings()
    }

    val actions = BookReaderActions(
        onBack = onBack,
        onToggleScrollMode = { scrollMode = !scrollMode },
        onToggleNightMode = viewModel::toggleNightMode,
        onGoTo = viewModel::setPage,
        onOpenTocEntry = viewModel::openTocEntry,
        onFontScale = { changeTextSettings { appPreferences.epubFontScale = it } },
        onFontFamily = { changeTextSettings { appPreferences.epubFontFamily = it } },
        onLineSpacing = { changeTextSettings { appPreferences.epubLineSpacing = it } },
        onAddBookmark = viewModel::addBookmark,
        onOpenBookmark = viewModel::jumpToBookmark,
        onDeleteBookmark = viewModel::deleteBookmark,
    )
    BookReaderScreen(readerState(viewModel, scrollMode, textSettings), actions) {
        ReaderPages(viewModel, scrollMode, textSettings)
    }
}

@Composable
private fun readerState(
    viewModel: BookReaderViewModel,
    scrollMode: Boolean,
    textSettings: EpubTextSettings,
): BookReaderUiState {
    val bookType by viewModel.bookType.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val chapter by viewModel.currentPage.collectAsState()
    val chapterCount by viewModel.totalPages.collectAsState()
    val nightMode by viewModel.nightMode.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val chosenTocEntry by viewModel.chosenTocEntry.collectAsState()
    // Only a loaded EPUB has contents and chapter names
    val epubReady = bookType == BookType.EPUB && !loading
    val tableOfContents = remember(epubReady) { if (epubReady) viewModel.getTableOfContents() else emptyList() }
    val chapterTitle = remember(epubReady, chapter, chosenTocEntry) {
        if (bookType == BookType.EPUB) viewModel.getChapterTitle(chapter) else null
    }
    return BookReaderUiState(
        bookType = bookType,
        loading = loading,
        scrollMode = scrollMode,
        nightMode = nightMode,
        chapter = chapter,
        chapterCount = chapterCount,
        chapterTitle = chapterTitle,
        tableOfContents = tableOfContents,
        chosenTocEntry = chosenTocEntry,
        bookmarks = bookmarks,
        textSettings = textSettings,
    )
}

private fun BookAppPreferences.readTextSettings() = EpubTextSettings(epubFontScale, epubFontFamily, epubLineSpacing)
