package org.jellyfin.mobile.books.downloads

import android.app.Application
import androidx.media3.datasource.DefaultHttpDataSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.jellyfin.mobile.common.downloads.ItemDownload
import org.jellyfin.mobile.testing.downloads.FakeItemDownloads
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/** Where the reader gets a book's file: the downloaded copy, or a temporary one fetched for reading. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BookFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer().apply { start() }
    private val downloads = FakeItemDownloads()
    private val book = "x".repeat(256 * 1024)

    private val files by lazy {
        BookFiles(
            downloads = downloads,
            cacheDir = folder.newFolder("cache"),
            dataSourceFactory = DefaultHttpDataSource.Factory(),
            uriOf = { server.url("/Items/$it/Download").toString() },
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `a downloaded book is read from its download, with nothing fetched`() = runTest {
        downloads.set("b1", ItemDownload.State.DONE)

        files.fileFor("b1").path shouldBe "/downloads/b1".replace('/', java.io.File.separatorChar)
        server.requestCount shouldBe 0
    }

    @Test
    fun `a book that isn't downloaded is fetched for reading`() = runTest {
        server.enqueue(MockResponse().setBody(book))

        files.fileFor("b1").readText() shouldBe book
    }

    @Test
    fun `a book fetched once is read again without fetching it twice`() = runTest {
        server.enqueue(MockResponse().setBody(book))
        files.fileFor("b1")

        files.fileFor("b1").readText() shouldBe book
        server.requestCount shouldBe 1
    }

    @Test
    fun `a fetch cut off part way is never opened as the book, and the next try gets the whole book`() = runTest {
        server.enqueue(MockResponse().setBody(book).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        shouldThrow<IOException> { files.fileFor("b1") }

        server.enqueue(MockResponse().setBody(book))
        files.fileFor("b1").readText() shouldBe book
    }
}
