package org.jellyfin.mobile.books.reader

import android.app.Application
import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.books.data.dao.BookBookmarkDao
import org.jellyfin.mobile.books.data.entity.BookBookmarkEntity
import org.jellyfin.mobile.books.downloads.BookFiles
import org.jellyfin.mobile.books.progress.BookProgressSync
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Regression coverage for a fix where the reader could show a blank screen (and an
 * unresponsive/"unscrollable" scroll mode) on the very first open of an EPUB.
 *
 * Root cause: `bookType` was published to the Fragment before `totalPages`/chapter data was
 * ready. The Fragment builds its page adapter the instant it observes the new book type, and
 * nothing ever re-triggers that build once the real chapter count arrives — so a premature
 * emission left the pager permanently stuck at zero pages.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BookReaderViewModelTest {

    @TempDir
    lateinit var cacheDir: File

    private val progressSync = mockk<BookProgressSync>(relaxed = true)
    private val bookFiles = mockk<BookFiles>()
    private val bookBookmarkDao = mockk<BookBookmarkDao>(relaxed = true)
    private val app = mockk<Application>(relaxed = true)

    private lateinit var viewModel: BookReaderViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        every { app.cacheDir } returns cacheDir
        every { app.applicationContext } returns app

        val epubFile = File(cacheDir, "source.epub").apply { writeBytes(buildMinimalEpub()) }
        coEvery { bookFiles.fileFor("book-1") } returns epubFile
        every { bookBookmarkDao.getForBook(any()) } returns flowOf(emptyList())

        viewModel = BookReaderViewModel(app, progressSync, bookFiles, bookBookmarkDao)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `totalPages is already populated by the time bookType is published as EPUB`() = runTest {
        viewModel.bookType.test {
            awaitItem() shouldBe BookType.UNKNOWN // initial value held by the StateFlow

            viewModel.loadBook("book-1", initialNightMode = false)

            val publishedType = awaitItem()
            publishedType shouldBe BookType.EPUB
            // The regression: totalPages must already reflect the real chapter count (2) at the
            // exact moment bookType flips — not still 0 from before loading finished.
            viewModel.totalPages.value shouldBe 2
        }
    }

    // --- table of contents -----------------------------------------------------------------------

    @Test
    fun `getTableOfContents returns the EPUB renderer's entries once loaded`() = runTest {
        viewModel.loadBook("book-1", initialNightMode = false)
        viewModel.loading.test {
            var loading = awaitItem()
            while (loading) loading = awaitItem()
        }

        // The minimal fixture EPUB has no real nav.xhtml/toc.ncx, so this exercises the
        // fallback-to-placeholders path (already covered directly in EpubRendererTest).
        viewModel.getTableOfContents().map { it.title } shouldBe listOf("Chapter 1", "Chapter 2")
    }

    @Test
    fun `getTableOfContents is empty before a book has been loaded`() {
        viewModel.getTableOfContents() shouldBe emptyList()
    }

    // --- bookmarks --------------------------------------------------------------------------------

    private suspend fun awaitLoaded() {
        viewModel.loading.test {
            var loading = awaitItem()
            while (loading) loading = awaitItem()
        }
    }

    @Test
    fun `addBookmark auto-labels from the chapter title for an EPUB`() = runTest {
        viewModel.loadBook("book-1", initialNightMode = false)
        awaitLoaded()
        viewModel.setPage(1) // "Chapter 2" (the fixture has no real TOC, so falls back to placeholders)

        viewModel.addBookmark()
        advanceUntilIdle()

        val captured = slot<BookBookmarkEntity>()
        coVerify { bookBookmarkDao.insert(capture(captured)) }
        captured.captured.label shouldBe "Chapter 2"
        captured.captured.pageIndex shouldBe 1
        captured.captured.bookItemId shouldBe "book-1"
    }

    @Test
    fun `addBookmark auto-labels from the page number for a PDF`() = runTest {
        val pdfFile = File(cacheDir, "pdf.book").apply {
            writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46)) // %PDF
        }
        coEvery { bookFiles.fileFor("book-pdf") } returns pdfFile
        every { bookBookmarkDao.getForBook("book-pdf") } returns flowOf(emptyList())

        viewModel.loadBook("book-pdf", initialNightMode = false)
        awaitLoaded()

        viewModel.addBookmark()
        advanceUntilIdle()

        val captured = slot<BookBookmarkEntity>()
        coVerify { bookBookmarkDao.insert(capture(captured)) }
        captured.captured.label shouldBe "Page 1"
    }

    @Test
    fun `addBookmark persists an optional note, blank ones treated as no note`() = runTest {
        viewModel.loadBook("book-1", initialNightMode = false)
        awaitLoaded()

        viewModel.addBookmark("A good quote here")
        advanceUntilIdle()

        val captured = slot<BookBookmarkEntity>()
        coVerify { bookBookmarkDao.insert(capture(captured)) }
        captured.captured.note shouldBe "A good quote here"
    }

    @Test
    fun `addBookmark treats a blank note the same as no note`() = runTest {
        viewModel.loadBook("book-1", initialNightMode = false)
        awaitLoaded()

        viewModel.addBookmark("   ")
        advanceUntilIdle()

        val captured = slot<BookBookmarkEntity>()
        coVerify { bookBookmarkDao.insert(capture(captured)) }
        captured.captured.note shouldBe null
    }

    @Test
    fun `bookmarks reflects the dao's flow for the loaded book`() = runTest {
        val entity = BookBookmarkEntity(
            id = 1L,
            bookItemId = "book-1",
            pageIndex = 0,
            label = "Chapter 1",
            note = null,
            createdAt = 0L,
        )
        every { bookBookmarkDao.getForBook("book-1") } returns flowOf(listOf(entity))

        viewModel.loadBook("book-1", initialNightMode = false)
        awaitLoaded()

        viewModel.bookmarks.value shouldBe listOf(entity)
    }

    @Test
    fun `deleteBookmark calls through to the dao`() = runTest {
        viewModel.deleteBookmark(42L)
        advanceUntilIdle()

        coVerify { bookBookmarkDao.delete(42L) }
    }

    @Test
    fun `jumpToBookmark seeks to the bookmark's page`() = runTest {
        viewModel.loadBook("book-1", initialNightMode = false)
        awaitLoaded()
        val entity = BookBookmarkEntity(
            id = 1L,
            bookItemId = "book-1",
            pageIndex = 1,
            label = "Chapter 2",
            note = null,
            createdAt = 0L,
        )

        viewModel.jumpToBookmark(entity)

        viewModel.currentPage.value shouldBe 1
    }

    @Test
    fun `loading resolves to false once the book has finished loading`() = runTest {
        viewModel.loadBook("book-1", initialNightMode = false)

        viewModel.loading.test {
            // loading may already be true by the time we start collecting; wait for it to settle.
            var loading = awaitItem()
            while (loading) loading = awaitItem()
            loading shouldBe false
        }
        viewModel.error.value shouldBe null
        viewModel.totalPages.value shouldBe 2
    }

    // --- loading again, as the fragment does when it's recreated (rotation) ---------------------

    @Test
    fun `loading the same book again keeps night mode as the reader left it`() = runTest {
        viewModel.loadBook("book-1", initialNightMode = false)
        awaitLoaded()
        viewModel.toggleNightMode()

        viewModel.loadBook("book-1", initialNightMode = false)
        awaitLoaded()

        viewModel.nightMode.value shouldBe true
    }

    @Test
    fun `loading the same book again stays on the chapter being read`() = runTest {
        viewModel.loadBook("book-1", initialNightMode = false)
        awaitLoaded()
        viewModel.setPage(1)

        viewModel.loadBook("book-1", initialNightMode = false)
        awaitLoaded()

        viewModel.currentPage.value shouldBe 1
    }

    @Test
    fun `a book that failed to load is tried again`() = runTest {
        val badFile = File(cacheDir, "bad.bin").apply { writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03)) }
        coEvery { bookFiles.fileFor("book-1") } returns badFile
        viewModel.loadBook("book-1", initialNightMode = false)
        awaitLoaded()
        viewModel.error.value shouldBe "Unsupported book format"

        coEvery { bookFiles.fileFor("book-1") } returns File(cacheDir, "source.epub")
        viewModel.loadBook("book-1", initialNightMode = false)
        awaitLoaded()

        viewModel.error.value shouldBe null
        viewModel.bookType.value shouldBe BookType.EPUB
    }

    // --- detectBookType --------------------------------------------------------------------------

    @Test
    fun `detects a PDF from its magic bytes`() {
        val file = File(cacheDir, "doc.pdf").apply { writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46)) } // %PDF
        viewModel.detectBookType(file) shouldBe BookType.PDF
    }

    @Test
    fun `detects an EPUB from its ZIP magic bytes`() {
        val file = File(cacheDir, "book.epub").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) } // PK..
        viewModel.detectBookType(file) shouldBe BookType.EPUB
    }

    @Test
    fun `a file under 4 bytes is UNKNOWN`() {
        val file = File(cacheDir, "tiny").apply { writeBytes(byteArrayOf(0x25)) }
        viewModel.detectBookType(file) shouldBe BookType.UNKNOWN
    }

    @Test
    fun `an unrecognized header is UNKNOWN`() {
        val file = File(cacheDir, "garbage").apply { writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03)) }
        viewModel.detectBookType(file) shouldBe BookType.UNKNOWN
    }

    @Test
    fun `any ZIP file is detected as EPUB, even if it is not really one`() {
        // Documents current (accepted) behavior: detection only looks at the ZIP magic bytes,
        // not whether the archive actually contains a valid EPUB structure.
        val file = File(cacheDir, "archive.zip").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x05, 0x06)) }
        viewModel.detectBookType(file) shouldBe BookType.EPUB
    }

    @Test
    fun `an unsupported format surfaces as an error and stops loading`() = runTest {
        val badFile = File(cacheDir, "bad.bin").apply { writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03)) }
        coEvery { bookFiles.fileFor("book-bad") } returns badFile

        viewModel.loadBook("book-bad", initialNightMode = false)

        viewModel.loading.test {
            // loading may already be true by the time we start collecting; wait for it to settle.
            var loading = awaitItem()
            while (loading) loading = awaitItem()
            loading shouldBe false
        }
        viewModel.error.value shouldBe "Unsupported book format"
        viewModel.bookType.value shouldBe BookType.UNKNOWN
    }

    // --- reading place -----------------------------------------------------------------------------

    @Test
    fun `a book opens at its place, here or from another device`() = runTest {
        coEvery { progressSync.startPage("book-1", totalPages = 2) } returns 1

        viewModel.loadBook("book-1", initialNightMode = false)
        awaitLoaded()

        viewModel.currentPage.value shouldBe 1
    }

    @Test
    fun `turning a page saves the place, to be sent to the server`() = runTest {
        viewModel.loadBook("book-1", initialNightMode = false)
        awaitLoaded()

        viewModel.setPage(1)

        verify { progressSync.save("book-1", page = 1, totalPages = 2) }
    }

    @Test
    fun `leaving a book sends its place`() = runTest {
        viewModel.loadBook("book-1", initialNightMode = false)
        awaitLoaded()

        viewModel.sendProgress()

        coVerify { progressSync.sendSoon() }
    }

    // --- setPage bounds clamping ------------------------------------------------------------------

    @Test
    fun `setPage sets the current page normally within range`() = runTest {
        viewModel.loadBook("book-1", initialNightMode = false)
        viewModel.loading.test {
            var loading = awaitItem()
            while (loading) loading = awaitItem()
        }

        viewModel.setPage(1)

        viewModel.currentPage.value shouldBe 1
    }

    @Test
    fun `setPage is a no-op when set to the already-current page`() = runTest {
        viewModel.loadBook("book-1", initialNightMode = false)
        viewModel.loading.test {
            var loading = awaitItem()
            while (loading) loading = awaitItem()
        }

        viewModel.setPage(0)

        viewModel.currentPage.value shouldBe 0
    }

    @Test
    fun `setPage clamps an out-of-range page into bounds`() = runTest {
        viewModel.loadBook("book-1", initialNightMode = false)
        viewModel.loading.test {
            var loading = awaitItem()
            while (loading) loading = awaitItem()
        }

        viewModel.setPage(99)
        viewModel.currentPage.value shouldBe 1 // totalPages (2) - 1

        viewModel.setPage(-5)
        viewModel.currentPage.value shouldBe 0
    }

    @Test
    fun `setPage before loadBook does not attempt to persist progress`() {
        viewModel.setPage(5)

        viewModel.currentPage.value shouldBe 5
        verify(exactly = 0) { progressSync.save(any(), any(), any()) }
    }

    private fun buildMinimalEpub(): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun writeEntry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
            writeEntry(
                "META-INF/container.xml",
                """
                <?xml version="1.0"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
                """.trimIndent(),
            )
            writeEntry(
                "OEBPS/content.opf",
                """
                <?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                    <manifest>
                        <item id="ch1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="ch2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                    </manifest>
                    <spine>
                        <itemref idref="ch1"/>
                        <itemref idref="ch2"/>
                    </spine>
                </package>
                """.trimIndent(),
            )
            writeEntry("OEBPS/chapter1.xhtml", "<html><body>Chapter 1</body></html>")
            writeEntry("OEBPS/chapter2.xhtml", "<html><body>Chapter 2</body></html>")
        }
        return out.toByteArray()
    }
}
