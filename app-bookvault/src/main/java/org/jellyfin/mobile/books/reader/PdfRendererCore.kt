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
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

class PdfRendererCore : Closeable {

    private var pdfRenderer: PdfRenderer? = null
    private val pageCache = LruCache<Int, Bitmap>(PAGE_CACHE_SIZE)

    val pageCount: Int
        get() = pdfRenderer?.pageCount ?: 0

    suspend fun openFile(file: File) = withContext(Dispatchers.IO) {
        close()
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(pfd)
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int, nightMode: Boolean): Bitmap =
        withContext(Dispatchers.Default) {
            pageCache[pageIndex]?.let { cached ->
                if (nightMode) applyNightMode(cached) else cached
            } ?: run {
                val renderer = checkNotNull(pdfRenderer) { "PdfRenderer not initialized" }
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
    }

    private companion object {
        const val PAGE_CACHE_SIZE = 12

        // Inverts each RGB channel (source * -1 + 255) for a simple night-mode color filter.
        const val COLOR_CHANNEL_OFFSET = 255f
    }
}
