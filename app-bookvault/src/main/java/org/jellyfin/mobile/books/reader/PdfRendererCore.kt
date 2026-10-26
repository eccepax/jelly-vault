package org.jellyfin.mobile.books.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

class PdfRendererCore : Closeable {

    private var pdfRenderer: PdfRenderer? = null
    private val pageCache = LruCache<Int, Bitmap>(PAGE_CACHE_SIZE)

    /** A PdfRenderer has one page open at a time: pages drawn side by side (a scroll) take turns. */
    private val pageOpen = Mutex()
    private var aspectRatios = FloatArray(0)

    val pageCount: Int
        get() = pdfRenderer?.pageCount ?: 0

    suspend fun openFile(file: File) = withContext(Dispatchers.IO) {
        close()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        // Every page's shape up front, so a scroll of them has its size before any is drawn
        aspectRatios = pageOpen.withLock {
            FloatArray(renderer.pageCount) { index -> renderer.openPage(index).use { it.width.toFloat() / it.height } }
        }
        pdfRenderer = renderer
    }

    /** The page at [pageIndex]'s width over its height, or 0 if there's no such page. */
    fun pageAspectRatio(pageIndex: Int): Float = aspectRatios.getOrElse(pageIndex) { 0f }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int, nightMode: Boolean): Bitmap =
        withContext(Dispatchers.Default) {
            pageCache[pageIndex]?.let { cached ->
                if (nightMode) applyNightMode(cached) else cached
            } ?: run {
                val renderer = checkNotNull(pdfRenderer) { "PdfRenderer not initialized" }
                pageOpen.withLock {
                    renderer.openPage(pageIndex).use { page ->
                        val scale = targetWidth.toFloat() / page.width
                        val bitmapHeight = (page.height * scale).toInt()
                        val bitmap = Bitmap.createBitmap(targetWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pageCache.put(pageIndex, bitmap)
                        if (nightMode) applyNightMode(bitmap) else bitmap
                    }
                }
            }
        }

    private fun applyNightMode(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, COLOR_CHANNEL_OFFSET,
                        0f, -1f, 0f, 0f, COLOR_CHANNEL_OFFSET,
                        0f, 0f, -1f, 0f, COLOR_CHANNEL_OFFSET,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    fun invalidateCache() {
        pageCache.evictAll()
    }

    override fun close() {
        pageCache.evictAll()
        pdfRenderer?.close()
        pdfRenderer = null
        aspectRatios = FloatArray(0)
    }

    private companion object {
        const val PAGE_CACHE_SIZE = 12

        // Inverts each RGB channel (source * -1 + 255) for a simple night-mode color filter.
        const val COLOR_CHANNEL_OFFSET = 255f
    }
}
