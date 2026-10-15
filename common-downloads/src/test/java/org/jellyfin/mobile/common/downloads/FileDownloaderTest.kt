package org.jellyfin.mobile.common.downloads

import android.app.Application
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FileDownloaderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer().apply { start() }
    private val content = ByteArray(64 * 1024) { (it % 251).toByte() }
    private val target by lazy { File(folder.root, "item-1") }
    private val partial by lazy { File(folder.root, "item-1.part") }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun downloader() = FileDownloader(
        uri = Uri.parse(server.url("/Items/1/Download").toString()),
        target = target,
        dataSource = DefaultHttpDataSource.Factory().createDataSource(),
    )

    private fun body(bytes: ByteArray) = Buffer().write(bytes)

    @Test
    fun `downloads the whole file and reports progress`() {
        server.enqueue(MockResponse().setBody(body(content)))
        var lastProgress = Triple(0L, 0L, 0f)

        downloader().download { length, downloaded, percent -> lastProgress = Triple(length, downloaded, percent) }

        target.readBytes() shouldBe content
        partial.exists() shouldBe false
        lastProgress shouldBe Triple(content.size.toLong(), content.size.toLong(), 100f)
    }

    @Test
    fun `a dropped connection leaves no file that looks finished`() {
        server.enqueue(
            MockResponse().setBody(body(content)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY),
        )

        shouldThrow<IOException> { downloader().download(null) }

        target.exists() shouldBe false
    }

    @Test
    fun `a server error leaves no file`() {
        server.enqueue(MockResponse().setResponseCode(500))

        shouldThrow<IOException> { downloader().download(null) }

        target.exists() shouldBe false
        partial.exists() shouldBe false
    }

    @Test
    fun `an interrupted download resumes where it stopped`() {
        val alreadyFetched = 10_000
        partial.writeBytes(content.copyOfRange(0, alreadyFetched))
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes $alreadyFetched-${content.size - 1}/${content.size}")
                .setBody(body(content.copyOfRange(alreadyFetched, content.size))),
        )

        downloader().download(null)

        server.takeRequest(5, TimeUnit.SECONDS)?.getHeader("Range") shouldBe "bytes=$alreadyFetched-"
        target.readBytes() shouldBe content
    }

    @Test
    fun `a server that ignores the resume request still gives the right file`() {
        partial.writeBytes(content.copyOfRange(0, 10_000))
        server.enqueue(MockResponse().setBody(body(content)))

        downloader().download(null)

        target.readBytes() shouldBe content
    }

    @Test
    fun `cancelling stops the download and keeps what was fetched for later`() {
        server.enqueue(MockResponse().setBody(body(content)))
        val downloader = downloader()

        shouldThrow<InterruptedException> {
            downloader.download { _, downloaded, _ -> if (downloaded > 0) downloader.cancel() }
        }

        target.exists() shouldBe false
        partial.exists() shouldBe true
    }

    @Test
    fun `remove deletes the file and any partial download`() {
        target.writeBytes(content)
        partial.writeBytes(content)

        downloader().remove()

        target.exists() shouldBe false
        partial.exists() shouldBe false
    }

    @Test
    fun `an already finished file is not fetched again`() {
        target.writeBytes(content)

        downloader().download(null)

        server.requestCount shouldBe 0
        target.readBytes() shouldBe content
    }
}
