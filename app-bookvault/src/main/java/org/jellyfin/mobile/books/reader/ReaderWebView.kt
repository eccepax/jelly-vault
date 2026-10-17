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
 */
fun readerWebView(context: Context, bookDir: () -> File?): WebView = WebView(context).apply {
    settings.javaScriptEnabled = false
    // A chapter's own pictures and stylesheets are files next to it
    settings.allowFileAccess = true
    @Suppress("DEPRECATION")
    settings.allowFileAccessFromFileURLs = false
    @Suppress("DEPRECATION")
    settings.allowUniversalAccessFromFileURLs = false
    settings.allowContentAccess = false
    settings.blockNetworkLoads = true
    webViewClient = ReaderWebViewClient(bookDir)
}

private class ReaderWebViewClient(private val bookDir: () -> File?) : WebViewClient() {

    private fun allowed(request: WebResourceRequest): Boolean {
        val dir = bookDir() ?: return false
        return isReaderRequestAllowed(request.url.toString(), dir)
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
        if (allowed(request)) null else WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = !allowed(request)
}
