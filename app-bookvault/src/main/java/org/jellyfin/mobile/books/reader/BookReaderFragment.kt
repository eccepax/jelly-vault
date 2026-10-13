package org.jellyfin.mobile.books.reader

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.app.BookAppPreferences
import org.jellyfin.mobile.books.databinding.FragmentReaderBinding
import org.jellyfin.mobile.common.ui.utils.padForNavigationBar
import org.jellyfin.mobile.common.ui.utils.padForStatusBar
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File
import java.io.IOException

class BookReaderFragment : Fragment() {

    private val bookItemId: String get() = requireArguments().getString(ARG_BOOK_ITEM_ID)!!
    private val viewModel: BookReaderViewModel by viewModel()
    private val appPreferences: BookAppPreferences by inject()

    private var _binding: FragmentReaderBinding? = null
    private val binding get() = _binding!!

    private var isScrollMode = true

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

    internal val dialogs by lazy {
        BookReaderDialogs(this, viewModel, appPreferences) { refreshAdapters() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentReaderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.padForNavigationBar()
        setupToolbar()
        setupPdfScrollMode()
        setupPdfPageFlipMode()

        val nightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        viewModel.loadBook(bookItemId, nightMode)

        observeViewModel()
    }

    private fun setupToolbar() {
        binding.toolbar.padForStatusBar()

        binding.toolbar.setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        binding.toolbar.inflateMenu(R.menu.menu_reader)
        binding.toolbar.setOnMenuItemClickListener { item -> onMenuItemClick(item) }
    }

    private fun onMenuItemClick(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_toggle_mode -> {
            toggleReadingMode()
            true
        }
        R.id.action_night_mode -> {
            viewModel.toggleNightMode()
            refreshAdapters()
            true
        }
        R.id.action_text_settings -> {
            dialogs.showTextSettingsChooser()
            true
        }
        R.id.action_contents -> {
            dialogs.showContents()
            true
        }
        R.id.action_add_bookmark -> {
            dialogs.showAddBookmarkDialog()
            true
        }
        R.id.action_bookmarks -> {
            dialogs.showBookmarksDialog()
            true
        }
        else -> false
    }

    private fun toggleReadingMode() {
        isScrollMode = !isScrollMode
        val loading = viewModel.loading.value
        when (viewModel.bookType.value) {
            BookType.PDF -> {
                binding.recyclerView.visibility = if (!loading && isScrollMode) View.VISIBLE else View.GONE
                binding.viewPager.visibility = if (!loading && !isScrollMode) View.VISIBLE else View.GONE
                val currentPage = viewModel.currentPage.value
                if (isScrollMode) {
                    (binding.recyclerView.layoutManager as LinearLayoutManager)
                        .scrollToPositionWithOffset(currentPage, 0)
                } else {
                    binding.viewPager.setCurrentItem(currentPage, false)
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
                launch { observeChapterBar() }
            }
        }
    }

    /**
     * Keeps the displayed page in sync with [BookReaderViewModel.currentPage] for jumps that
     * originate *outside* a swipe/scroll (table of contents, bookmarks, the page-jump dialog) —
     * those call `viewModel.setPage(...)` directly, which updates the ViewModel but has no other
     * path back to the ViewPager2/RecyclerView. A swipe-driven change is already reflected in the
     * displayed position by the time this collects it, so the equality check makes this a no-op
     * for that case — only programmatic jumps actually move anything here.
     */
    private suspend fun observeCurrentPage() {
        viewModel.currentPage.collect { page -> syncDisplayedPage(page) }
    }

    private suspend fun observeChapterBar() {
        combine(viewModel.currentPage, viewModel.totalPages, ::Pair).collect { (chapter, chapterCount) ->
            updateChapterBar(chapter, chapterCount)
        }
    }

    /** The EPUB chapter bar: the chapter's name and position, with arrows only where there's somewhere to go. */
    private fun updateChapterBar(chapter: Int, chapterCount: Int) {
        val title = if (viewModel.bookType.value == BookType.EPUB) viewModel.getChapterTitle(chapter) else null
        binding.chapterTitle.text = title
        binding.chapterTitle.isVisible = title != null
        binding.chapterPosition.text = getString(R.string.chapter_position, chapter + 1, chapterCount)
        binding.btnPrevChapter.isEnabled = chapter > 0
        binding.btnNextChapter.isEnabled = chapter < chapterCount - 1
    }

    private fun syncDisplayedPage(page: Int) {
        if (viewModel.loading.value) return
        when (viewModel.bookType.value) {
            BookType.EPUB -> {
                if (binding.viewPager.currentItem != page) binding.viewPager.setCurrentItem(page, false)
            }
            BookType.PDF -> if (isScrollMode) {
                val layoutManager = binding.recyclerView.layoutManager as? LinearLayoutManager
                if (layoutManager != null && layoutManager.findFirstVisibleItemPosition() != page) {
                    suppressPdfScrollTracking = true
                    layoutManager.scrollToPositionWithOffset(page, 0)
                }
            } else {
                if (binding.viewPager.currentItem != page) binding.viewPager.setCurrentItem(page, false)
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
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            if (!loading && viewModel.bookType.value != BookType.EPUB) {
                applyPageVisibilityAndScroll()
            }
        }
    }

    private fun applyPageVisibilityAndScroll() {
        if (isScrollMode) {
            binding.recyclerView.visibility = View.VISIBLE
            binding.viewPager.visibility = View.GONE
        } else {
            binding.recyclerView.visibility = View.GONE
            binding.viewPager.visibility = View.VISIBLE
        }
        refreshAdapters()
        val savedPage = viewModel.currentPage.value
        if (isScrollMode) {
            (binding.recyclerView.layoutManager as LinearLayoutManager)
                .scrollToPositionWithOffset(savedPage, 0)
        } else {
            binding.viewPager.setCurrentItem(savedPage, false)
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
        binding.recyclerView.visibility = View.GONE
        binding.viewPager.visibility = View.VISIBLE

        val adapter = EpubChapterAdapter(
            chapterCount = { viewModel.totalPages.value },
            getChapterFile = { viewModel.getChapterFile(it) },
            nightMode = { viewModel.nightMode.value },
            textSettings = {
                EpubTextSettings(
                    appPreferences.epubFontScale,
                    appPreferences.epubFontFamily,
                    appPreferences.epubLineSpacing,
                )
            },
        )
        binding.viewPager.adapter = adapter
        // Scroll mode: no swipe (use chapter nav bar). Page flip: swipe between chapters.
        binding.viewPager.isUserInputEnabled = !isScrollMode

        // Chapter nav bar: visible in scroll mode only
        binding.chapterNavBar.visibility = if (isScrollMode) View.VISIBLE else View.GONE
        binding.btnPrevChapter.setOnClickListener {
            val current = viewModel.currentPage.value
            if (current > 0) binding.viewPager.setCurrentItem(current - 1, false)
        }
        binding.btnNextChapter.setOnClickListener {
            val current = viewModel.currentPage.value
            if (current < viewModel.totalPages.value - 1) binding.viewPager.setCurrentItem(current + 1, false)
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.setPage(position)
            }
        })

        val savedPage = viewModel.currentPage.value
        if (savedPage > 0) binding.viewPager.setCurrentItem(savedPage, false)
    }

    private fun setupPdfScrollMode() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = PdfScrollAdapter(
            pageCount = { viewModel.totalPages.value },
            renderPage = { viewModel.renderPage(it) },
            scope = viewLifecycleOwner.lifecycleScope,
        )
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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
        binding.viewPager.adapter = PdfPageAdapter(
            pageCount = { viewModel.totalPages.value },
            renderPage = { viewModel.renderPage(it) },
            scope = viewLifecycleOwner.lifecycleScope,
        )
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                viewModel.setPage(position)
            }
        })
    }

    private fun refreshAdapters() {
        when (viewModel.bookType.value) {
            BookType.EPUB -> binding.viewPager.adapter?.notifyDataSetChanged()
            else -> {
                binding.recyclerView.adapter?.notifyDataSetChanged()
                binding.viewPager.adapter?.notifyDataSetChanged()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.reportPlaybackStopped()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
    private val nightMode: () -> Boolean,
    private val textSettings: () -> EpubTextSettings,
) : RecyclerView.Adapter<EpubChapterAdapter.Holder>() {

    inner class Holder(val webView: WebView) : RecyclerView.ViewHolder(webView)

    override fun getItemCount() = chapterCount()

    // Unique view type per position prevents recycling stale WebView content
    override fun getItemViewType(position: Int) = position

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val wv = WebView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            @Suppress("DEPRECATION")
            settings.allowFileAccess = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            settings.javaScriptEnabled = true
        }
        return Holder(wv)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val file = getChapterFile(position) ?: return
        val isNight = nightMode()
        val textCss = textSettings().toCssText()
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

        holder.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return request.url.scheme != "file"
            }

            override fun onPageFinished(view: WebView, url: String) {
                // Text-appearance settings apply to every chapter, regardless of night mode.
                view.evaluateJavascript("document.documentElement.style.cssText+='$textCss'", null)
                if (isNight) {
                    view.evaluateJavascript(
                        "document.documentElement.style.filter='invert(1) hue-rotate(180deg)'",
                        null,
                    )
                }
            }
        }
        holder.webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
    }
}
