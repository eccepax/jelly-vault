package org.jellyfin.mobile.books.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.books.databinding.ReaderPagesBinding
import java.io.File
import java.io.IOException

/**
 * The book's pages, as views of their own that the reader draws between its bars: a PDF as a
 * scroll or one page at a time, an EPUB one chapter at a time. Kept in step with [viewModel] while
 * [follow] runs. Rendering runs in [scope], which goes with the reader.
 */
internal class ReaderPages(
    context: Context,
    private val viewModel: BookReaderViewModel,
    private val scope: CoroutineScope,
    scrollMode: Boolean,
    private val textSettings: () -> EpubTextSettings,
) {
    private val pages = ReaderPagesBinding.inflate(LayoutInflater.from(context))

    val view: View get() = pages.root

    /** Read as a scroll, or a page (EPUB: a chapter) at a time; changing it shows the same page the other way. */
    var scrollMode = scrollMode
        set(value) {
            field = value
            showReadingMode()
        }

    /**
     * True after a programmatic jump (TOC/bookmark/page-jump dialog) has driven the PDF
     * RecyclerView to its target page, until the user next drags it themselves. [PdfScrollAdapter]
     * binds page bitmaps asynchronously, so items above/below the target can still be changing
     * height as their bitmaps arrive well after the jump — each such relayout fires the scroll
     * listener below, which would otherwise persist whatever transient (and often wrong) position
     * that relayout lands on. A single post-frame timer isn't long enough to outlast that (bitmap
     * decoding can take several frames), so tracking only resumes on a real user drag instead —
     * see the on-device bug this fixed (jumping to page 10 ended up persisting page 3).
     */
    private var suppressPdfScrollTracking = false

    init {
        setupPdfScrollMode(context)
        setupPager()
    }

    /** Follows the book as it opens and moves, until cancelled. */
    suspend fun follow() = coroutineScope {
        launch { observeBookType() }
        launch { observeLoading() }
        launch { observeCurrentPage() }
        // A chapter chosen from the contents opens at its place in its file, even the file already open
        launch {
            viewModel.chosenTocEntry.collect { entry ->
                entry?.let { (pages.viewPager.adapter as? EpubChapterAdapter)?.notifyItemChanged(it.spineIndex) }
            }
        }
    }

    /** Draws the pages again: the text settings or night mode changed. */
    fun refresh() {
        when (viewModel.bookType.value) {
            BookType.EPUB -> pages.viewPager.adapter?.notifyDataSetChanged()
            else -> {
                pages.recyclerView.adapter?.notifyDataSetChanged()
                pages.viewPager.adapter?.notifyDataSetChanged()
            }
        }
    }

    private fun showReadingMode() {
        pages.root.reset()
        pages.root.childScrollsVertically = scrollMode
        val loading = viewModel.loading.value
        when (viewModel.bookType.value) {
            BookType.PDF -> {
                pages.recyclerView.visibility = if (!loading && scrollMode) View.VISIBLE else View.GONE
                pages.viewPager.visibility = if (!loading && !scrollMode) View.VISIBLE else View.GONE
                val currentPage = viewModel.currentPage.value
                if (scrollMode) {
                    (pages.recyclerView.layoutManager as LinearLayoutManager)
                        .scrollToPositionWithOffset(currentPage, 0)
                } else {
                    pages.viewPager.setCurrentItem(currentPage, false)
                }
            }
            BookType.EPUB -> setupEpubMode()
            else -> Unit
        }
    }

    /**
     * Keeps the displayed page in sync with [BookReaderViewModel.currentPage] for jumps that
     * originate *outside* a swipe/scroll (table of contents, bookmarks, the page-jump dialog, the
     * chapter arrows) — those call `viewModel.setPage(...)` directly, which updates the ViewModel
     * but has no other path back to the ViewPager2/RecyclerView. A swipe-driven change is already
     * reflected in the displayed position by the time this collects it, so the equality check
     * makes this a no-op for that case — only programmatic jumps actually move anything here.
     */
    private suspend fun observeCurrentPage() {
        viewModel.currentPage.collect { page -> syncDisplayedPage(page) }
    }

    private fun syncDisplayedPage(page: Int) {
        if (viewModel.loading.value) return
        when (viewModel.bookType.value) {
            BookType.EPUB -> {
                if (pages.viewPager.currentItem != page) pages.viewPager.setCurrentItem(page, false)
            }
            BookType.PDF -> if (scrollMode) {
                val layoutManager = pages.recyclerView.layoutManager as? LinearLayoutManager
                if (layoutManager != null && layoutManager.findFirstVisibleItemPosition() != page) {
                    suppressPdfScrollTracking = true
                    layoutManager.scrollToPositionWithOffset(page, 0)
                }
            } else {
                if (pages.viewPager.currentItem != page) pages.viewPager.setCurrentItem(page, false)
            }
            else -> Unit
        }
    }

    private suspend fun observeBookType() {
        viewModel.bookType.collect { type ->
            when (type) {
                BookType.EPUB -> setupEpubMode()
                else -> Unit
            }
        }
    }

    private suspend fun observeLoading() {
        viewModel.loading.collect { loading ->
            if (!loading && viewModel.bookType.value != BookType.EPUB) {
                // A PDF's pages are pictures: they zoom, where an EPUB's text has text settings
                pages.root.zoomEnabled = viewModel.bookType.value == BookType.PDF
                applyPageVisibilityAndScroll()
            }
        }
    }

    private fun applyPageVisibilityAndScroll() {
        pages.root.childScrollsVertically = scrollMode
        if (scrollMode) {
            pages.recyclerView.visibility = View.VISIBLE
            pages.viewPager.visibility = View.GONE
        } else {
            pages.recyclerView.visibility = View.GONE
            pages.viewPager.visibility = View.VISIBLE
        }
        refresh()
        val savedPage = viewModel.currentPage.value
        if (scrollMode) {
            (pages.recyclerView.layoutManager as LinearLayoutManager)
                .scrollToPositionWithOffset(savedPage, 0)
        } else {
            pages.viewPager.setCurrentItem(savedPage, false)
        }
    }

    private fun setupEpubMode() {
        pages.root.zoomEnabled = false
        pages.recyclerView.visibility = View.GONE
        pages.viewPager.visibility = View.VISIBLE

        if (pages.viewPager.adapter !is EpubChapterAdapter) {
            pages.viewPager.adapter = EpubChapterAdapter(
                chapterCount = { viewModel.totalPages.value },
                getChapterFile = { viewModel.getChapterFile(it) },
                anchorAt = { position ->
                    viewModel.chosenTocEntry.value?.takeIf { it.spineIndex == position }?.anchor
                },
                bookDir = { viewModel.getBookDir() },
                nightMode = { viewModel.nightMode.value },
                textSettings = textSettings,
            )
        }
        // Scroll mode: no swipe (the chapter bar moves between chapters). Page flip: swipe between chapters.
        pages.viewPager.isUserInputEnabled = !scrollMode

        val savedPage = viewModel.currentPage.value
        if (savedPage > 0) pages.viewPager.setCurrentItem(savedPage, false)
    }

    private fun setupPdfScrollMode(context: Context) {
        pages.recyclerView.layoutManager = LinearLayoutManager(context)
        pages.recyclerView.adapter = PdfScrollAdapter(
            pageCount = { viewModel.totalPages.value },
            renderPage = { viewModel.renderPage(it) },
            scope = scope,
        )
        pages.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) suppressPdfScrollTracking = false
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (suppressPdfScrollTracking) return
                val lm = recyclerView.layoutManager as LinearLayoutManager
                val first = lm.findFirstVisibleItemPosition()
                if (first != RecyclerView.NO_POSITION) viewModel.setPage(first)
            }
        })
    }

    /** The pager shows a PDF's pages until an EPUB's chapters take it over; either way, the page it shows is the place. */
    private fun setupPager() {
        pages.viewPager.adapter = PdfPageAdapter(
            pageCount = { viewModel.totalPages.value },
            renderPage = { viewModel.renderPage(it) },
            scope = scope,
        )
        pages.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // The zoom is the whole pager's: a new page (a swipe once zoomed out, or a jump) starts whole
                if (!scrollMode) pages.root.reset()
                viewModel.setPage(position)
            }
        })
    }
}

private class PdfScrollAdapter(
    private val pageCount: () -> Int,
    private val renderPage: suspend (Int) -> Bitmap?,
    private val scope: CoroutineScope,
) : RecyclerView.Adapter<PdfScrollAdapter.Holder>() {

    inner class Holder(val image: ImageView) : RecyclerView.ViewHolder(image)

    override fun getItemCount() = pageCount()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val iv = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            adjustViewBounds = true
        }
        return Holder(iv)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        scope.launch(Dispatchers.Main) {
            val bitmap = withContext(Dispatchers.Default) { renderPage(position) }
            holder.image.setImageBitmap(bitmap)
        }
    }
}

private class PdfPageAdapter(
    private val pageCount: () -> Int,
    private val renderPage: suspend (Int) -> Bitmap?,
    private val scope: CoroutineScope,
) : RecyclerView.Adapter<PdfPageAdapter.Holder>() {

    inner class Holder(val image: ImageView) : RecyclerView.ViewHolder(image)

    override fun getItemCount() = pageCount()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val iv = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        return Holder(iv)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        scope.launch(Dispatchers.Main) {
            val bitmap = withContext(Dispatchers.Default) { renderPage(position) }
            holder.image.setImageBitmap(bitmap)
        }
    }
}

private class EpubChapterAdapter(
    private val chapterCount: () -> Int,
    private val getChapterFile: (Int) -> File?,
    /** Where in its file to open the chapter at a position: the element id a contents entry starts at. */
    private val anchorAt: (Int) -> String?,
    private val bookDir: () -> File?,
    private val nightMode: () -> Boolean,
    private val textSettings: () -> EpubTextSettings,
) : RecyclerView.Adapter<EpubChapterAdapter.Holder>() {

    inner class Holder(val webView: WebView) : RecyclerView.ViewHolder(webView)

    override fun getItemCount() = chapterCount()

    // Unique view type per position prevents recycling stale WebView content
    override fun getItemViewType(position: Int) = position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        // EPUBs are untrusted: no scripts, and nothing but the book's own files (see readerWebView)
        val wv = readerWebView(parent.context, bookDir, chapterPage = ::styledPage).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        return Holder(wv)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val file = getChapterFile(position) ?: return
        // The book's table of contents could name any file as a chapter; only its own are shown
        val dir = bookDir() ?: return
        if (!isReaderRequestAllowed(file.toURI().toString(), dir)) return
        val isNight = nightMode()

        // Set the WebView's own canvas color up front so there's no white flash/gap before the
        // page (or its CSS filter, for night mode) paints — and while short chapters that don't
        // fill the screen leave the WebView's native background showing beneath the content.
        holder.webView.setBackgroundColor(if (isNight) Color.BLACK else Color.WHITE)
        // CSS filter (used below for night mode) silently no-ops on WebView's default hardware
        // compositing layer; software rendering is required for it to actually paint.
        holder.webView.setLayerType(
            if (isNight) View.LAYER_TYPE_SOFTWARE else View.LAYER_TYPE_HARDWARE,
            null,
        )

        // Opened by its own URL, so an anchor opens it at that element; the page comes from styledPage
        val anchor = anchorAt(position)?.let { "#" + Uri.encode(it) }.orEmpty()
        holder.webView.loadUrl(Uri.fromFile(file).toString() + anchor)
    }

    /** [file] as the reader shows it: the text settings and night mode come with it, as a stylesheet (no scripts needed). */
    private fun styledPage(file: File): String? {
        val html = try { file.readText() } catch (e: IOException) { return null }
        return styledChapterHtml(html, textSettings().toCssText(), nightMode())
    }
}
