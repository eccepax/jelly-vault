package org.jellyfin.mobile.books.reader

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.databinding.FragmentComposeBinding
import org.jellyfin.mobile.books.databinding.ReaderPagesBinding
import org.jellyfin.mobile.common.ui.theme.AppTheme
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.io.IOException

class BookReaderFragment : Fragment() {

    private val bookItemId: String get() = requireArguments().getString(ARG_BOOK_ITEM_ID)!!
    private val viewModel: BookReaderViewModel by viewModel()
    private val appPreferences: BookAppPreferences by inject()

    private var _binding: FragmentComposeBinding? = null
    private val binding get() = _binding!!

    /** The book's pages: views of their own, which the Compose screen draws between its bars. */
    private var _pages: ReaderPagesBinding? = null
    private val pages get() = _pages!!

    private var isScrollMode by mutableStateOf(true)
    private var textSettings by mutableStateOf(EpubTextSettings())

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentComposeBinding.inflate(inflater, container, false)
        _pages = ReaderPagesBinding.inflate(inflater)
        return binding.composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.requestApplyInsets(binding.composeView)
        textSettings = readTextSettings()
        setupPdfScrollMode()
        setupPdfPageFlipMode()

        val nightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        viewModel.loadBook(bookItemId, nightMode)

        val actions = BookReaderActions(
            onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
            onToggleScrollMode = ::toggleReadingMode,
            onToggleNightMode = {
                viewModel.toggleNightMode()
                refreshAdapters()
            },
            onGoTo = viewModel::setPage,
            onFontScale = { changeTextSettings { appPreferences.epubFontScale = it } },
            onFontFamily = { changeTextSettings { appPreferences.epubFontFamily = it } },
            onLineSpacing = { changeTextSettings { appPreferences.epubLineSpacing = it } },
            onAddBookmark = viewModel::addBookmark,
            onOpenBookmark = viewModel::jumpToBookmark,
            onDeleteBookmark = viewModel::deleteBookmark,
        )
        val pagesView = pages.root
        binding.composeView.setContent {
            AppTheme(VaultBrand.BookVault) {
                BookReaderScreen(collectState(), actions) {
                    AndroidView(
                        factory = {
                            // A recomposed screen takes the same views back
                            (pagesView.parent as? ViewGroup)?.removeView(pagesView)
                            pagesView
                        },
                        // Left to wrap, the pages (which only fill their parent) would get no room at all
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        observeViewModel()
    }

    @Composable
    private fun collectState(): BookReaderUiState {
        val bookType by viewModel.bookType.collectAsState()
        val loading by viewModel.loading.collectAsState()
        val chapter by viewModel.currentPage.collectAsState()
        val chapterCount by viewModel.totalPages.collectAsState()
        val nightMode by viewModel.nightMode.collectAsState()
        val bookmarks by viewModel.bookmarks.collectAsState()
        // Only a loaded EPUB has contents and chapter names
        val epubReady = bookType == BookType.EPUB && !loading
        val tableOfContents = remember(epubReady) { if (epubReady) viewModel.getTableOfContents() else emptyList() }
        val chapterTitle = remember(epubReady, chapter) {
            if (bookType == BookType.EPUB) viewModel.getChapterTitle(chapter) else null
        }
        return BookReaderUiState(
            bookType = bookType,
            loading = loading,
            scrollMode = isScrollMode,
            nightMode = nightMode,
            chapter = chapter,
            chapterCount = chapterCount,
            chapterTitle = chapterTitle,
            tableOfContents = tableOfContents,
            bookmarks = bookmarks,
            textSettings = textSettings,
        )
    }

    private fun readTextSettings() = EpubTextSettings(
        appPreferences.epubFontScale,
        appPreferences.epubFontFamily,
        appPreferences.epubLineSpacing,
    )

    /** Saves a text setting, then shows it: on the screen, and in the chapters drawn again with it. */
    private fun changeTextSettings(save: () -> Unit) {
        save()
        textSettings = readTextSettings()
        refreshAdapters()
    }

    private fun toggleReadingMode() {
        isScrollMode = !isScrollMode
        pages.root.reset()
        pages.root.childScrollsVertically = isScrollMode
        val loading = viewModel.loading.value
        when (viewModel.bookType.value) {
            BookType.PDF -> {
                pages.recyclerView.visibility = if (!loading && isScrollMode) View.VISIBLE else View.GONE
                pages.viewPager.visibility = if (!loading && !isScrollMode) View.VISIBLE else View.GONE
                val currentPage = viewModel.currentPage.value
                if (isScrollMode) {
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

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { observeBookType() }
                launch { observeLoading() }
                launch { observeError() }
                launch { observeCurrentPage() }
            }
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
            BookType.PDF -> if (isScrollMode) {
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
        pages.root.childScrollsVertically = isScrollMode
        if (isScrollMode) {
            pages.recyclerView.visibility = View.VISIBLE
            pages.viewPager.visibility = View.GONE
        } else {
            pages.recyclerView.visibility = View.GONE
            pages.viewPager.visibility = View.VISIBLE
        }
        refreshAdapters()
        val savedPage = viewModel.currentPage.value
        if (isScrollMode) {
            (pages.recyclerView.layoutManager as LinearLayoutManager)
                .scrollToPositionWithOffset(savedPage, 0)
        } else {
            pages.viewPager.setCurrentItem(savedPage, false)
        }
    }

    private suspend fun observeError() {
        viewModel.error.collect { error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupEpubMode() {
        pages.root.zoomEnabled = false
        pages.recyclerView.visibility = View.GONE
        pages.viewPager.visibility = View.VISIBLE

        val adapter = EpubChapterAdapter(
            chapterCount = { viewModel.totalPages.value },
            getChapterFile = { viewModel.getChapterFile(it) },
            bookDir = { viewModel.getBookDir() },
            nightMode = { viewModel.nightMode.value },
            textSettings = { textSettings },
        )
        pages.viewPager.adapter = adapter
        // Scroll mode: no swipe (the chapter bar moves between chapters). Page flip: swipe between chapters.
        pages.viewPager.isUserInputEnabled = !isScrollMode

        pages.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.setPage(position)
            }
        })

        val savedPage = viewModel.currentPage.value
        if (savedPage > 0) pages.viewPager.setCurrentItem(savedPage, false)
    }

    private fun setupPdfScrollMode() {
        pages.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        pages.recyclerView.adapter = PdfScrollAdapter(
            pageCount = { viewModel.totalPages.value },
            renderPage = { viewModel.renderPage(it) },
            scope = viewLifecycleOwner.lifecycleScope,
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

    private fun setupPdfPageFlipMode() {
        pages.viewPager.adapter = PdfPageAdapter(
            pageCount = { viewModel.totalPages.value },
            renderPage = { viewModel.renderPage(it) },
            scope = viewLifecycleOwner.lifecycleScope,
        )
        pages.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // The zoom is the whole pager's: a new page (a swipe once zoomed out, or a jump) starts whole
                if (!isScrollMode) pages.root.reset()
                viewModel.setPage(position)
            }
        })
    }

    private fun refreshAdapters() {
        when (viewModel.bookType.value) {
            BookType.EPUB -> pages.viewPager.adapter?.notifyDataSetChanged()
            else -> {
                pages.recyclerView.adapter?.notifyDataSetChanged()
                pages.viewPager.adapter?.notifyDataSetChanged()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.sendProgress()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        _pages = null
    }

    companion object {
        const val ARG_BOOK_ITEM_ID = "book_item_id"

        fun newInstance(bookItemId: String) = BookReaderFragment().apply {
            arguments = Bundle().apply { putString(ARG_BOOK_ITEM_ID, bookItemId) }
        }
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
        val wv = readerWebView(parent.context, bookDir).apply {
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
        val baseUrl = "file://${file.parentFile!!.absolutePath}/"
        val html = try { file.readText() } catch (e: IOException) { return }

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

        // The text settings and night mode come with the page, as a stylesheet: no scripts needed
        val page = styledChapterHtml(html, textSettings().toCssText(), isNight)
        holder.webView.loadDataWithBaseURL(baseUrl, page, "text/html", "UTF-8", null)
    }
}
