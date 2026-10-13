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
data class EpubTocEntry(val title: String, val spineIndex: Int)

class EpubRenderer(private val context: Context) : Closeable {

    private var spineFiles: List<File> = emptyList()
    private var tocEntries: List<EpubTocEntry> = emptyList()
    private val tocParser = EpubTocParser()

    val chapterCount: Int get() = spineFiles.size

    suspend fun openFile(epubFile: File, bookItemId: String) = withContext(Dispatchers.IO) {
        close()
        val dir = File(context.cacheDir, "epub_$bookItemId")
        if (!File(dir, "META-INF/container.xml").exists()) {
            dir.deleteRecursively()
            dir.mkdirs()
            extractEpub(epubFile, dir)
        }
        spineFiles = parseSpine(dir)
        tocEntries = buildTableOfContents(dir).takeIf { it.isNotEmpty() } ?: fallbackToc()
        Timber.d("EPUB opened: ${spineFiles.size} chapters, ${tocEntries.size} TOC entries")
    }

    fun getChapterFile(index: Int): File? = spineFiles.getOrNull(index)

    /** Real chapter titles from the EPUB's own table of contents, or [fallbackToc] if none is found. */
    fun getTableOfContents(): List<EpubTocEntry> = tocEntries

    private fun fallbackToc(): List<EpubTocEntry> =
        spineFiles.indices.map { index -> EpubTocEntry("Chapter ${index + 1}", index) }

    private fun extractEpub(epubFile: File, destDir: File) {
        ZipFile(epubFile).use { zip ->
            zip.entries().asSequence().forEach { entry -> extractEntry(zip, entry, destDir) }
        }
    }

    private fun extractEntry(zip: ZipFile, entry: ZipEntry, destDir: File) {
        val outFile = File(destDir, entry.name)
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
        return parseOpf(opfFile, opfDir)
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
    private fun parseOpf(opfFile: File, opfDir: File): List<File> {
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
            spineRefs.mapNotNull { idref ->
                manifest[idref]?.let { href -> File(opfDir, href).takeIf { it.exists() } }
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
        spineFiles = emptyList()
    }
}
