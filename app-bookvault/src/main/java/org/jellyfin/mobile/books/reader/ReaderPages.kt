package org.jellyfin.mobile.books.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.drop
import org.jellyfin.mobile.books.R
import java.io.File
import java.io.IOException

/** The tag of the pages as a whole, which zoom (see [PageZoomScale]). */
const val READER_PAGES = "reader_pages"

/** The tag of the page (PDF) or chapter (EPUB) at [page], from 0. */
fun readerPageTag(page: Int) = "reader_page_$page"

/** A page's shape (width over height) until its own is known: A4's. */
private const val DEFAULT_PAGE_ASPECT_RATIO = 210f / 297f

/** A contents entry chosen, the [count]th: its chapter opens at the entry's place again, even if it's showing. */
private data class TocJump(val count: Int, val spineIndex: Int)

/**
 * The book's pages, between the reader's bars: a PDF as a scroll or one page at a time, an EPUB one
 * chapter at a time. They follow [viewModel]'s place, and moving through them moves it.
 */
@Composable
fun ReaderPages(
    viewModel: BookReaderViewModel,
    scrollMode: Boolean,
    textSettings: EpubTextSettings,
    modifier: Modifier = Modifier,
) {
    val bookType by viewModel.bookType.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val pageCount by viewModel.totalPages.collectAsState()
    val nightMode by viewModel.nightMode.collectAsState()
    val zoom = remember { PageZoomState() }
    // A PDF's pages are pictures: they zoom, where an EPUB's text has text settings
    val zoomable = bookType == BookType.PDF
    // Another way of reading starts whole
    LaunchedEffect(zoomable, scrollMode) { zoom.reset() }

    Box(modifier.fillMaxSize().testTag(READER_PAGES).pageZoom(zoom, zoomable, scrollVertically = scrollMode)) {
        // The pages open at the book's place, so not until it's known
        if (loading) return@Box
        when (bookType) {
            BookType.PDF -> if (scrollMode) {
                PdfScroll(viewModel, pageCount, nightMode)
            } else {
                // The zoom is the whole pager's: a new page (a swipe once zoomed out, or a jump) starts whole
                ReaderPager(viewModel, pageCount, userScrollEnabled = true, onTurn = zoom::reset) { page ->
                    PdfPage(viewModel, page, nightMode, Modifier.fillMaxSize())
                }
            }
            BookType.EPUB -> EpubChapters(viewModel, pageCount, scrollMode, textSettings, nightMode)
            BookType.UNKNOWN -> Unit
        }
    }
}

/**
 * A PDF as a scroll, each page sized from the start, so pages drawn later move nothing. Only the
 * reader's own scrolling moves the place: a jump there, or settling at the end, doesn't.
 */
@Composable
private fun PdfScroll(viewModel: BookReaderViewModel, pageCount: Int, nightMode: Boolean) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = viewModel.currentPage.value)
    var following by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { if (it is DragInteraction.Start) following = true }
    }
    // Jumps from outside the scroll (contents, bookmarks, the page-jump dialog)
    LaunchedEffect(listState) {
        viewModel.currentPage.collect { page ->
            if (page != listState.firstVisibleItemIndex) {
                following = false
                listState.scrollToItem(page)
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { if (following) viewModel.setPage(it) }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(pageCount) { page ->
            val aspectRatio = viewModel.pageAspectRatio(page).takeIf { it > 0f } ?: DEFAULT_PAGE_ASPECT_RATIO
            PdfPage(viewModel, page, nightMode, Modifier.fillMaxWidth().aspectRatio(aspectRatio))
        }
    }
}

/** A PDF's pages or an EPUB's chapters, a page at a time; the one settled on is the place. */
@Composable
private fun ReaderPager(
    viewModel: BookReaderViewModel,
    pageCount: Int,
    userScrollEnabled: Boolean,
    onTurn: () -> Unit = {},
    page: @Composable (Int) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = viewModel.currentPage.value) { pageCount }
    // Jumps from outside the pager (contents, bookmarks, the page-jump dialog, the chapter arrows)
    LaunchedEffect(pagerState) {
        viewModel.currentPage.collect { if (it != pagerState.currentPage) pagerState.scrollToPage(it) }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect {
            onTurn()
            viewModel.setPage(it)
        }
    }
    HorizontalPager(state = pagerState, userScrollEnabled = userScrollEnabled, modifier = Modifier.fillMaxSize()) {
        page(it)
    }
}

@Composable
private fun PdfPage(viewModel: BookReaderViewModel, page: Int, nightMode: Boolean, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, page, nightMode) { value = viewModel.renderPage(page) }
    Box(modifier.testTag(readerPageTag(page))) {
        bitmap?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = stringResource(R.string.pdf_page, page + 1),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Scroll mode: no swipe (the chapter bar moves between chapters). Page flip: swipe between chapters. */
@Composable
private fun EpubChapters(
    viewModel: BookReaderViewModel,
    chapterCount: Int,
    scrollMode: Boolean,
    textSettings: EpubTextSettings,
    nightMode: Boolean,
) {
    val chosenTocEntry by viewModel.chosenTocEntry.collectAsState()
    var tocJump by remember { mutableStateOf(TocJump(0, -1)) }
    // A chapter chosen from the contents opens at its place in its file, even the file already open
    LaunchedEffect(viewModel) {
        viewModel.tocJumps.collect { tocJump = TocJump(tocJump.count + 1, it.spineIndex) }
    }
    ReaderPager(viewModel, chapterCount, userScrollEnabled = !scrollMode) { chapter ->
        val load = ChapterLoad(
            anchor = chosenTocEntry?.takeIf { it.spineIndex == chapter }?.anchor,
            jump = tocJump.takeIf { it.spineIndex == chapter }?.count ?: 0,
            textSettings = textSettings,
            nightMode = nightMode,
        )
        Chapter(viewModel, chapter, load)
    }
}

/** What a chapter is shown with: a change loads it again. */
private data class ChapterLoad(
    /** Where in its file to open the chapter: the element id a contents entry starts at. */
    val anchor: String?,
    val jump: Int,
    val textSettings: EpubTextSettings,
    val nightMode: Boolean,
)

/** What a chapter's WebView has loaded so far. */
private class ChapterLoads {
    var shown: ChapterLoad? = null

    /** Loads so far, giving each its own address (see [chapterUrl]). */
    var count = 0
}

@Composable
private fun Chapter(viewModel: BookReaderViewModel, chapter: Int, load: ChapterLoad) {
    // Read as the WebView asks for the page, so the newest are used
    val current by rememberUpdatedState(load)
    val loads = remember { ChapterLoads() }
    AndroidView(
        factory = { context ->
            // EPUBs are untrusted: no scripts, and nothing but the book's own files (see readerWebView)
            readerWebView(context, viewModel::getBookDir, chapterPage = { file -> styledPage(file, current) }).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        },
        modifier = Modifier.fillMaxSize().testTag(readerPageTag(chapter)),
        onRelease = WebView::destroy,
        update = { webView ->
            if (loads.shown != load) {
                loads.shown = load
                webView.show(viewModel.getChapterFile(chapter), viewModel.getBookDir(), load, ++loads.count)
            }
        },
    )
}

private fun WebView.show(file: File?, bookDir: File?, load: ChapterLoad, loadNumber: Int) {
    // The book's table of contents could name any file as a chapter; only its own are shown
    if (file == null || bookDir == null || !isReaderRequestAllowed(file.toURI().toString(), bookDir)) return
    // Set the WebView's own canvas color up front so there's no white flash/gap before the
    // page (or its CSS filter, for night mode) paints — and while short chapters that don't
    // fill the screen leave the WebView's native background showing beneath the content.
    setBackgroundColor(if (load.nightMode) Color.BLACK else Color.WHITE)
    // CSS filter (used for night mode) silently no-ops on WebView's default hardware
    // compositing layer; software rendering is required for it to actually paint.
    setLayerType(if (load.nightMode) View.LAYER_TYPE_SOFTWARE else View.LAYER_TYPE_HARDWARE, null)
    // Opened by its own URL, so an anchor opens it at that element; the page comes from styledPage
    loadUrl(chapterUrl(file, load.anchor, loadNumber))
}

/** [file] as the reader shows it: the text settings and night mode come with it, as a stylesheet (no scripts needed). */
private fun styledPage(file: File, load: ChapterLoad): String? {
    val html = try { file.readText() } catch (e: IOException) { return null }
    return styledChapterHtml(html, load.textSettings.toCssText(), load.nightMode)
}
