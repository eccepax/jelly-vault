package org.jellyfin.mobile.books.reader

import android.app.Application
import android.net.Uri
import android.webkit.WebResourceRequest
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** The WebView a chapter is shown in, locked down for untrusted EPUBs (see [ReaderPageTest]). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ReaderWebViewTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val book = File(context.cacheDir, "epub_1").apply { mkdirs() }
    private val chapter = File(book, "chapter2.xhtml")
    private val webView = readerWebView(
        context,
        bookDir = { book },
        chapterPage = { file -> "<html>styled ${file.name}</html>".takeIf { file == chapter } },
    )

    private fun request(url: String, mainFrame: Boolean = false) = mockk<WebResourceRequest> {
        every { this@mockk.url } returns Uri.parse(url)
        every { isForMainFrame } returns mainFrame
    }

    @Test
    fun `a chapter opened at a place in it is answered with the styled page, so it opens there without scripts`() {
        val client = shadowOf(webView).webViewClient.shouldNotBeNull()
        val url = Uri.fromFile(chapter).toString() + "#ch5"

        val page = client.shouldInterceptRequest(webView, request(url, mainFrame = true)).shouldNotBeNull()

        page.mimeType shouldBe "text/html"
        page.data.readBytes().decodeToString() shouldBe "<html>styled chapter2.xhtml</html>"
    }

    @Test
    fun `only the page itself is restyled, not what it loads`() {
        val client = shadowOf(webView).webViewClient.shouldNotBeNull()

        client.shouldInterceptRequest(webView, request(Uri.fromFile(chapter).toString())).shouldBeNull()
    }

    @Test
    fun `scripts can't run`() {
        webView.settings.javaScriptEnabled shouldBe false
    }

    @Test
    fun `a page can't read other files, content providers or the network on its own`() {
        @Suppress("DEPRECATION")
        webView.settings.allowFileAccessFromFileURLs shouldBe false
        @Suppress("DEPRECATION")
        webView.settings.allowUniversalAccessFromFileURLs shouldBe false
        webView.settings.allowContentAccess shouldBe false
        webView.settings.blockNetworkLoads shouldBe true
    }

    @Test
    fun `the book's own pictures load, and anything else is answered with nothing`() {
        val client = shadowOf(webView).webViewClient.shouldNotBeNull()
        val cover = File(book, "images/cover.jpg").toURI().toString()
        val database = File(context.dataDir, "databases/jellyfin.db").toURI().toString()

        client.shouldInterceptRequest(webView, request(cover)).shouldBeNull()
        val blocked = client.shouldInterceptRequest(webView, request("https://example.com/steal")).shouldNotBeNull()
        blocked.data.readBytes().size shouldBe 0
        client.shouldInterceptRequest(webView, request(database)).shouldNotBeNull()
    }

    @Test
    fun `pictures written into the chapter itself load`() {
        val client = shadowOf(webView).webViewClient.shouldNotBeNull()

        client.shouldInterceptRequest(webView, request("data:image/png;base64,iVBORw0KGgo=")).shouldBeNull()
    }

    @Test
    fun `links don't leave the book`() {
        val client = shadowOf(webView).webViewClient.shouldNotBeNull()

        client.shouldOverrideUrlLoading(webView, request("https://example.com")) shouldBe true
        client.shouldOverrideUrlLoading(webView, request(File(book, "chapter2.xhtml").toURI().toString())) shouldBe false
    }
}
