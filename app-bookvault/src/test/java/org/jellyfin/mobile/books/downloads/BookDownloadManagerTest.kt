package org.jellyfin.mobile.books.downloads

import android.content.Context
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jellyfin.mobile.common.data.dao.DownloadRecordDao
import org.jellyfin.mobile.common.data.entity.DownloadRecord
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID
import kotlin.io.path.createTempDirectory

/**
 * Uses a real [MockWebServer] + real [OkHttpClient] (no mocking of OkHttp's own types, which are
 * awkward to mock directly) and a real temp directory for [Context.getFilesDir], per this
 * session's established "prefer a real object under a faked boundary" pattern. [ApiClient] and
 * [DownloadRecordDao] are mocked — the former's `baseUrl`/`accessToken` are real abstract members
 * (not extension properties with logic of their own), so plain mocking is safe here.
 */
class BookDownloadManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var tempDir: File
    private val apiClient = mockk<ApiClient>(relaxed = true)
    private val downloadRecordDao = mockk<DownloadRecordDao>(relaxed = true)
    private lateinit var manager: BookDownloadManager

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        every { apiClient.baseUrl } returns server.url("/").toString().trimEnd('/')
        every { apiClient.accessToken } returns "test-token"

        tempDir = createTempDirectory("bookvault-test").toFile()
        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns tempDir

        manager = BookDownloadManager(context, apiClient, OkHttpClient(), downloadRecordDao)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    private fun book(id: UUID = UUID.randomUUID(), name: String = "Some Book") =
        BaseItemDto(id = id, type = BaseItemKind.BOOK, name = name)

    @Test
    fun `downloads a new book to disk and records it`() = runTest {
        val itemId = UUID.randomUUID()
        val bytes = "fake epub contents".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(String(bytes)))

        manager.downloadBook(book(id = itemId))

        val downloadedFile = File(tempDir, "books/$itemId")
        downloadedFile.exists() shouldBe true
        downloadedFile.readBytes() shouldBe bytes

        val recorded = slot<DownloadRecord>()
        coVerify { downloadRecordDao.insert(capture(recorded)) }
        recorded.captured.itemId shouldBe itemId.toString()
        recorded.captured.mediaType shouldBe "BOOK"
        recorded.captured.fileSizeBytes shouldBe bytes.size.toLong()
    }

    @Test
    fun `requests the correct download URL, including the access token`() = runTest {
        val itemId = UUID.randomUUID()
        server.enqueue(MockResponse().setResponseCode(200).setBody("x"))

        manager.downloadBook(book(id = itemId))

        server.takeRequest().path shouldBe "/Items/${itemId.toString().replace("-", "")}/Download?api_key=test-token"
    }

    @Test
    fun `a null access token sends no api_key rather than the literal string null`() = runTest {
        every { apiClient.accessToken } returns null
        val itemId = UUID.randomUUID()
        server.enqueue(MockResponse().setResponseCode(200).setBody("x"))

        manager.downloadBook(book(id = itemId))

        server.takeRequest().path shouldBe "/Items/${itemId.toString().replace("-", "")}/Download"
    }

    @Test
    fun `skips the HTTP request entirely when already downloaded, but still re-records it`() = runTest {
        val itemId = UUID.randomUUID()
        File(tempDir, "books").apply { mkdirs() }
        File(tempDir, "books/$itemId").writeText("already here")

        manager.downloadBook(book(id = itemId))

        server.requestCount shouldBe 0
        // Current behavior: the record is unconditionally re-inserted even on the skip path,
        // touching downloadedAt. Documented here rather than left implicit.
        coVerify { downloadRecordDao.insert(any()) }
    }

    @Test
    fun `a failed HTTP response throws instead of writing a partial file or recording it`() = runTest {
        val itemId = UUID.randomUUID()
        server.enqueue(MockResponse().setResponseCode(404))

        val result = runCatching { manager.downloadBook(book(id = itemId)) }

        result.isFailure shouldBe true
        File(tempDir, "books/$itemId").exists() shouldBe false
        coVerify(exactly = 0) { downloadRecordDao.insert(any()) }
    }

    @Test
    fun `getDownloadedFile returns the file only once it exists`() {
        val itemId = UUID.randomUUID().toString()

        manager.getDownloadedFile(itemId) shouldBe null

        File(tempDir, "books").apply { mkdirs() }
        File(tempDir, "books/$itemId").writeText("here")

        manager.getDownloadedFile(itemId)?.exists() shouldBe true
    }

    @Test
    fun `deleteDownload removes the file and the record`() = runTest {
        val itemId = UUID.randomUUID().toString()
        File(tempDir, "books").apply { mkdirs() }
        val file = File(tempDir, "books/$itemId").apply { writeText("here") }

        manager.deleteDownload(itemId)

        file.exists() shouldBe false
        coVerify { downloadRecordDao.delete(itemId) }
    }

    @Test
    fun `deleteDownload still removes the record when no file exists`() = runTest {
        val itemId = UUID.randomUUID().toString()

        manager.deleteDownload(itemId)

        coVerify { downloadRecordDao.delete(itemId) }
    }
}
