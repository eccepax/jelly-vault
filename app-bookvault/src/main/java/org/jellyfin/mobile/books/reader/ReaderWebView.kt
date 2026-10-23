package org.jellyfin.mobile.books.reader

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.File

/**
 * A WebView for one chapter of an untrusted EPUB: no scripts, and it loads only the files of the
 * book extracted to [bookDir] (see [isReaderRequestAllowed]). Everything else, the network and the
 * app's other files included, is answered with nothing.
 *
 * A chapter is opened by its own file URL, so a `#anchor` on it opens the page at that element
 * without scripts; [chapterPage] answers for the file with the page as the reader shows it (its
 * text settings and night mode), or null to show the file as it is.
 */
fun readerWebView(
    context: Context,
    bookDir: () -> File?,
    chapterPage: (File) -> String? = { null },
): WebView = WebView(context).apply {
    settings.javaScriptEnabled = false
    // A chapter's own pictures and stylesheets are files next to it
    settings.allowFileAccess = true
    @Suppress("DEPRECATION")
    settings.allowFileAccessFromFileURLs = false
    @Suppress("DEPRECATION")
    settings.allowUniversalAccessFromFileURLs = false
    settings.allowContentAccess = false
    settings.blockNetworkLoads = true
    webViewClient = ReaderWebViewClient(bookDir, chapterPage)
}

private class ReaderWebViewClient(
    private val bookDir: () -> File?,
    private val chapterPage: (File) -> String?,
) : WebViewClient() {

    private fun allowed(request: WebResourceRequest): Boolean {
        val dir = bookDir() ?: return false
        return isReaderRequestAllowed(request.url.toString(), dir)
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = when {
        !allowed(request) -> WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
        // The chapter itself, as the reader shows it; what it loads (pictures, stylesheets) as it is
        request.isForMainFrame -> request.url.path?.let { chapterPage(File(it)) }
            ?.let { WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(it.toByteArray())) }
        else -> null
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !allowed(request)
}
