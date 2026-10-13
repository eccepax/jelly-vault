package org.jellyfin.mobile.books.reader

import android.app.Application
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Basic functionality coverage for EPUB spine parsing.
 *
 * Also guards against a regression risk from switching the parser off `org.xmlpull.v1` (an
 * Android-bundled API that throws "not mocked" in plain JVM unit tests, making this class
 * previously untestable off-device) to `javax.xml.parsers` DOM parsing.
 */
class EpubRendererTest {

    @TempDir
    lateinit var cacheDir: File

    private lateinit var app: Application

    @BeforeEach
    fun setUp() {
        app = mockk(relaxed = true)
        every { app.cacheDir } returns cacheDir
    }

    private fun buildEpub(vararg entries: Pair<String, String>): File {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return File(cacheDir, "${System.nanoTime()}.epub").apply { writeBytes(out.toByteArray()) }
    }

    private val containerXml = """
        <?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
            <rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
            </rootfiles>
        </container>
    """.trimIndent()

    private fun opfXml(vararg chapters: String) = """
        <?xml version="1.0"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
            <manifest>
                ${chapters.mapIndexed { i, href -> """<item id="ch$i" href="$href" media-type="application/xhtml+xml"/>""" }.joinToString(
        "\n",
    )}
            </manifest>
            <spine>
                ${chapters.indices.joinToString("\n") { i -> """<itemref idref="ch$i"/>""" }}
            </spine>
        </package>
    """.trimIndent().trim()

    @Test
    fun `parses chapters in spine order`() = runTest {
        val epub = buildEpub(
            "META-INF/container.xml" to containerXml,
            "OEBPS/content.opf" to opfXml("chapter1.xhtml", "chapter2.xhtml", "chapter3.xhtml"),
            "OEBPS/chapter1.xhtml" to "<html><body>One</body></html>",
            "OEBPS/chapter2.xhtml" to "<html><body>Two</body></html>",
            "OEBPS/chapter3.xhtml" to "<html><body>Three</body></html>",
        )

        val renderer = EpubRenderer(app)
        renderer.openFile(epub, "book-order")

        renderer.chapterCount shouldBe 3
        renderer.getChapterFile(0)?.name shouldBe "chapter1.xhtml"
        renderer.getChapterFile(1)?.name shouldBe "chapter2.xhtml"
        renderer.getChapterFile(2)?.name shouldBe "chapter3.xhtml"
        renderer.getChapterFile(3) shouldBe null
    }

    @Test
    fun `handles a namespace-prefixed opf (eg opf-colon-item)`() = runTest {
        val prefixedOpf = """
            <?xml version="1.0"?>
            <opf:package xmlns:opf="http://www.idpf.org/2007/opf" version="2.0">
                <opf:manifest>
                    <opf:item id="ch0" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                </opf:manifest>
                <opf:spine>
                    <opf:itemref idref="ch0"/>
                </opf:spine>
            </opf:package>
        """.trimIndent()
        val epub = buildEpub(
            "META-INF/container.xml" to containerXml,
            "OEBPS/content.opf" to prefixedOpf,
            "OEBPS/chapter1.xhtml" to "<html><body>One</body></html>",
        )

        val renderer = EpubRenderer(app)
        renderer.openFile(epub, "book-prefixed")

        renderer.chapterCount shouldBe 1
    }

    @Test
    fun `zero chapters when container xml is missing`() = runTest {
        val epub = buildEpub("OEBPS/content.opf" to opfXml("chapter1.xhtml"))

        val renderer = EpubRenderer(app)
        renderer.openFile(epub, "book-missing-container")

        renderer.chapterCount shouldBe 0
    }

    @Test
    fun `skips spine entries whose manifest target file does not exist`() = runTest {
        val epub = buildEpub(
            "META-INF/container.xml" to containerXml,
            "OEBPS/content.opf" to opfXml("chapter1.xhtml", "missing.xhtml"),
            "OEBPS/chapter1.xhtml" to "<html><body>One</body></html>",
            // "missing.xhtml" intentionally not included in the zip
        )

        val renderer = EpubRenderer(app)
        renderer.openFile(epub, "book-missing-chapter-file")

        renderer.chapterCount shouldBe 1
    }

    // --- table of contents ------------------------------------------------------------------------

    @Test
    fun `parses a real chapter-title TOC from an EPUB3 nav xhtml`() = runTest {
        val opfWithNav = """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <manifest>
                    <item id="ch0" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="ch1" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="navdoc" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                </manifest>
                <spine>
                    <itemref idref="ch0"/>
                    <itemref idref="ch1"/>
                </spine>
            </package>
        """.trimIndent()
        val navXhtml = """
            <?xml version="1.0"?>
            <html xmlns:epub="http://www.idpf.org/2007/ops">
                <body>
                    <nav epub:type="toc">
                        <ol>
                            <li><a href="chapter1.xhtml">Prologue</a></li>
                            <li><a href="chapter2.xhtml#top">Chapter One</a></li>
                        </ol>
                    </nav>
                </body>
            </html>
        """.trimIndent()
        val epub = buildEpub(
            "META-INF/container.xml" to containerXml,
            "OEBPS/content.opf" to opfWithNav,
            "OEBPS/nav.xhtml" to navXhtml,
            "OEBPS/chapter1.xhtml" to "<html><body>One</body></html>",
            "OEBPS/chapter2.xhtml" to "<html><body>Two</body></html>",
        )

        val renderer = EpubRenderer(app)
        renderer.openFile(epub, "book-nav-toc")

        val toc = renderer.getTableOfContents()
        toc.map { it.title } shouldBe listOf("Prologue", "Chapter One")
        toc.map { it.spineIndex } shouldBe listOf(0, 1)
    }

    @Test
    fun `parses a real chapter-title TOC from an EPUB2 toc ncx`() = runTest {
        val opfWithNcx = """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="2.0">
                <manifest>
                    <item id="ch0" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="ch1" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                </manifest>
                <spine toc="ncx">
                    <itemref idref="ch0"/>
                    <itemref idref="ch1"/>
                </spine>
            </package>
        """.trimIndent()
        val tocNcx = """
            <?xml version="1.0"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/">
                <navMap>
                    <navPoint id="np1">
                        <navLabel><text>Prologue</text></navLabel>
                        <content src="chapter1.xhtml"/>
                    </navPoint>
                    <navPoint id="np2">
                        <navLabel><text>Chapter One</text></navLabel>
                        <content src="chapter2.xhtml"/>
                    </navPoint>
                </navMap>
            </ncx>
        """.trimIndent()
        val epub = buildEpub(
            "META-INF/container.xml" to containerXml,
            "OEBPS/content.opf" to opfWithNcx,
            "OEBPS/toc.ncx" to tocNcx,
            "OEBPS/chapter1.xhtml" to "<html><body>One</body></html>",
            "OEBPS/chapter2.xhtml" to "<html><body>Two</body></html>",
        )

        val renderer = EpubRenderer(app)
        renderer.openFile(epub, "book-ncx-toc")

        val toc = renderer.getTableOfContents()
        toc.map { it.title } shouldBe listOf("Prologue", "Chapter One")
        toc.map { it.spineIndex } shouldBe listOf(0, 1)
    }

    @Test
    fun `falls back to Chapter N placeholders when no TOC is present`() = runTest {
        val epub = buildEpub(
            "META-INF/container.xml" to containerXml,
            "OEBPS/content.opf" to opfXml("chapter1.xhtml", "chapter2.xhtml"),
            "OEBPS/chapter1.xhtml" to "<html><body>One</body></html>",
            "OEBPS/chapter2.xhtml" to "<html><body>Two</body></html>",
        )

        val renderer = EpubRenderer(app)
        renderer.openFile(epub, "book-no-toc")

        val toc = renderer.getTableOfContents()
        toc.map { it.title } shouldBe listOf("Chapter 1", "Chapter 2")
        toc.map { it.spineIndex } shouldBe listOf(0, 1)
    }

    @Test
    fun `TOC entries whose href does not resolve to a spine file are skipped`() = runTest {
        val opfWithNav = """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                <manifest>
                    <item id="ch0" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="navdoc" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                </manifest>
                <spine>
                    <itemref idref="ch0"/>
                </spine>
            </package>
        """.trimIndent()
        val navXhtml = """
            <?xml version="1.0"?>
            <html xmlns:epub="http://www.idpf.org/2007/ops">
                <body>
                    <nav epub:type="toc">
                        <ol>
                            <li><a href="chapter1.xhtml">Prologue</a></li>
                            <li><a href="does-not-exist.xhtml">Ghost Chapter</a></li>
                        </ol>
                    </nav>
                </body>
            </html>
        """.trimIndent()
        val epub = buildEpub(
            "META-INF/container.xml" to containerXml,
            "OEBPS/content.opf" to opfWithNav,
            "OEBPS/nav.xhtml" to navXhtml,
            "OEBPS/chapter1.xhtml" to "<html><body>One</body></html>",
        )

        val renderer = EpubRenderer(app)
        renderer.openFile(epub, "book-toc-ghost-entry")

        renderer.getTableOfContents().map { it.title } shouldBe listOf("Prologue")
    }
}
