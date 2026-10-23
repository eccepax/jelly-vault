package org.jellyfin.mobile.books.reader

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.Closeable
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/** One entry in an EPUB's table of contents — a human-readable [title] and the spine chapter it opens. */
/**
 * A table-of-contents entry: its chapter's file ([spineIndex]), and where in that file it starts
 * ([anchor], the element id after the link's `#`), as several chapters can share one file.
 */
data class EpubTocEntry(val title: String, val spineIndex: Int, val anchor: String? = null)

/**
 * The index in [toc] of the entry being read in the file at [spineIndex]: [chosen] from the contents
 * while still in its file, else the file's first entry, else the entry before it (a part's title
 * page or a chapter's continuation file has none of its own). -1 before the first entry.
 */
internal fun tocEntryAt(toc: List<EpubTocEntry>, spineIndex: Int, chosen: EpubTocEntry?): Int {
    val chosenHere = chosen?.takeIf { it.spineIndex == spineIndex }?.let(toc::indexOf) ?: -1
    if (chosenHere >= 0) return chosenHere
    val first = toc.indexOfFirst { it.spineIndex == spineIndex }
    return if (first >= 0) first else toc.indexOfLast { it.spineIndex < spineIndex }
}

/**
 * The title for the spine item at [spineIndex]: the closest table-of-contents entry at or before it,
 * since items like a part's title page or a chapter's continuation files have no entry of their own.
 * Null before the first entry (a cover, say).
 */
internal fun chapterTitleAt(toc: List<EpubTocEntry>, spineIndex: Int): String? =
    toc.filter { it.spineIndex <= spineIndex }.maxByOrNull { it.spineIndex }?.title

class EpubRenderer(private val context: Context) : Closeable {

    private var spineFiles: List<File> = emptyList()
    private var tocEntries: List<EpubTocEntry> = emptyList()

    /** The book's own table of contents, without [fallbackToc]'s made-up "Chapter N" names. */
    private var bookToc: List<EpubTocEntry> = emptyList()
    private val tocParser = EpubTocParser()

    val chapterCount: Int get() = spineFiles.size

    /** Where the open book is extracted; the reader loads nothing from outside it. */
    @Volatile // Read on the WebView's own thread, for every file a page asks for
    var bookDir: File? = null
        private set

    suspend fun openFile(epubFile: File, bookItemId: String) = withContext(Dispatchers.IO) {
        close()
        val dir = File(context.cacheDir, "epub_$bookItemId")
        if (!File(dir, "META-INF/container.xml").exists()) {
            dir.deleteRecursively()
            dir.mkdirs()
            extractEpub(epubFile, dir)
        }
        bookDir = dir
        spineFiles = parseSpine(dir)
        bookToc = buildTableOfContents(dir)
        tocEntries = bookToc.takeIf { it.isNotEmpty() } ?: fallbackToc()
        Timber.d("EPUB opened: ${spineFiles.size} chapters, ${tocEntries.size} TOC entries")
    }

    fun getChapterFile(index: Int): File? = spineFiles.getOrNull(index)

    /** Real chapter titles from the EPUB's own table of contents, or [fallbackToc] if none is found. */
    fun getTableOfContents(): List<EpubTocEntry> = tocEntries

    /** The chapter's name from the book's own table of contents, or null if it doesn't have one. */
    fun chapterTitleAt(spineIndex: Int): String? = chapterTitleAt(bookToc, spineIndex)

    private fun fallbackToc(): List<EpubTocEntry> =
        spineFiles.indices.map { index -> EpubTocEntry("Chapter ${index + 1}", index) }

    private fun extractEpub(epubFile: File, destDir: File) {
        ZipFile(epubFile).use { zip ->
            zip.entries().asSequence().forEach { entry -> extractEntry(zip, entry, destDir) }
        }
    }

    private fun extractEntry(zip: ZipFile, entry: ZipEntry, destDir: File) {
        val outFile = File(destDir, entry.name)
        // Zip Slip: a malicious EPUB's "../" entries would otherwise overwrite the app's own files
        if (!outFile.isInside(destDir)) {
            Timber.w("Skipping EPUB entry outside the book's folder: %s", entry.name)
            return
        }
        if (entry.isDirectory) {
            outFile.mkdirs()
            return
        }
        outFile.parentFile?.mkdirs()
        zip.getInputStream(entry).use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun parseSpine(dir: File): List<File> {
        val opfPath = parseContainerXml(File(dir, "META-INF/container.xml")) ?: return emptyList()
        val opfFile = File(dir, opfPath)
        val opfDir = opfFile.parentFile ?: dir
        return parseOpf(opfFile, opfDir, dir)
    }

    /**
     * XML parsing here can throw any of [org.xml.sax.SAXException], [java.io.IOException], or
     * [javax.xml.parsers.ParserConfigurationException] depending on what's malformed about a given
     * EPUB — all treated the same way (log and fall back to "unparseable").
     */
    @Suppress("TooGenericExceptionCaught")
    private fun parseContainerXml(containerFile: File): String? {
        if (!containerFile.exists()) return null
        return try {
            val root = parseXml(containerFile)
            root.findDescendant("rootfile")?.getAttribute("full-path")?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Timber.e(e, "container.xml parse failed")
            null
        }
    }

    /** See [parseContainerXml] — same rationale for the broad catch. */
    @Suppress("TooGenericExceptionCaught")
    private fun parseOpf(opfFile: File, opfDir: File, bookDir: File): List<File> {
        if (!opfFile.exists()) return emptyList()
        return try {
            val root = parseXml(opfFile)
            val manifest = root.findDescendant("manifest")
                ?.children("item")
                ?.mapNotNull { item ->
                    val id = item.getAttribute("id").takeIf { it.isNotEmpty() }
                    val href = item.getAttribute("href").takeIf { it.isNotEmpty() }
                    if (id != null && href != null) id to href else null
                }
                ?.toMap()
                .orEmpty()
            val spineRefs = root.findDescendant("spine")
                ?.children("itemref")
                ?.mapNotNull { it.getAttribute("idref").takeIf { idref -> idref.isNotEmpty() } }
                .orEmpty()
            // The spine could name any file as a chapter; only the book's own are
            spineRefs.mapNotNull { idref ->
                manifest[idref]
                    ?.let { href -> resolveEpubHref(opfDir, href) }
                    ?.takeIf { it.exists() && it.isInside(bookDir) }
            }
        } catch (e: Exception) {
            Timber.e(e, "OPF parse failed")
            emptyList()
        }
    }

    private fun buildTableOfContents(dir: File): List<EpubTocEntry> {
        val opfPath = parseContainerXml(File(dir, "META-INF/container.xml")) ?: return emptyList()
        val opfFile = File(dir, opfPath)
        val opfDir = opfFile.parentFile ?: dir
        if (!opfFile.exists()) return emptyList()
        val opfRoot = parseXmlOrNull(opfFile) ?: return emptyList()
        return tocParser.parse(opfRoot, opfDir, spineFiles)
    }

    override fun close() {
        bookDir = null
        spineFiles = emptyList()
        bookToc = emptyList()
    }
}
