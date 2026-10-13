package org.jellyfin.mobile.books.reader

import org.w3c.dom.Element
import java.io.File

/**
 * Resolves an EPUB's real chapter-title table of contents: EPUB3 `nav.xhtml` (a manifest item
 * with `properties="nav"`, containing `<nav epub:type="toc">`) if present, else EPUB2 `toc.ncx`
 * (referenced by the OPF's `<spine toc="...">` attribute). Extracted out of [EpubRenderer] to keep
 * spine-extraction and TOC-extraction — two independent XML dialects — as separate, focused units.
 */
internal class EpubTocParser {

    /** Returns an empty list — the caller falls back to placeholder titles — if nothing resolves. */
    fun parse(opfRoot: Element, opfDir: File, spineFiles: List<File>): List<EpubTocEntry> {
        val manifestItems = opfRoot.findDescendant("manifest")?.children("item").orEmpty()
        val navHref = manifestItems.firstOrNull { item ->
            item.getAttribute("properties").split(' ').contains("nav")
        }?.getAttribute("href")

        val rawEntries: List<Pair<String, File>> = if (navHref != null) {
            val navFile = File(opfDir, navHref)
            parseNavToc(navFile).map { (title, href) -> title to resolveHref(navFile, href) }
        } else {
            val ncxId = opfRoot.findDescendant("spine")?.getAttribute("toc")?.takeIf { it.isNotEmpty() }
            val ncxHref = manifestItems.firstOrNull { it.getAttribute("id") == ncxId }?.getAttribute("href")
            ncxHref?.let { href ->
                val ncxFile = File(opfDir, href)
                parseNcxToc(ncxFile).map { (title, entryHref) -> title to resolveHref(ncxFile, entryHref) }
            }.orEmpty()
        }

        return rawEntries.mapNotNull { (title, file) -> resolveTocEntry(title, file, spineFiles) }
    }

    /** Resolves a TOC entry's `href` (which may carry a `#fragment`) relative to its own source file. */
    private fun resolveHref(sourceFile: File, href: String): File =
        File(sourceFile.parentFile, href.substringBefore('#'))

    private fun resolveTocEntry(title: String, targetFile: File, spineFiles: List<File>): EpubTocEntry? {
        val spineIndex = spineFiles.indexOfFirst { it.canonicalFile == targetFile.canonicalFile }
        return if (spineIndex >= 0) EpubTocEntry(title, spineIndex) else null
    }

    /** Parses an EPUB3 nav document's `<nav epub:type="toc">` into (title, href) pairs, in order. */
    private fun parseNavToc(navFile: File): List<Pair<String, String>> {
        if (!navFile.exists()) return emptyList()
        val root = parseXmlOrNull(navFile) ?: return emptyList()
        val tocNav = root.descendants("nav").firstOrNull { it.getAttribute("epub:type") == "toc" }
            ?: return emptyList()
        return tocNav.descendants("a").mapNotNull { anchor ->
            val href = anchor.getAttribute("href").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val title = anchor.textContent.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            title to href
        }
    }

    /** Parses an EPUB2 `toc.ncx`'s `<navMap>` into (title, href) pairs, in document order. */
    private fun parseNcxToc(ncxFile: File): List<Pair<String, String>> {
        if (!ncxFile.exists()) return emptyList()
        val root = parseXmlOrNull(ncxFile) ?: return emptyList()
        return root.descendants("navPoint").mapNotNull { navPoint ->
            val title = navPoint.findDescendant("text")?.textContent?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val href = navPoint.children("content").firstOrNull()?.getAttribute("src")?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            title to href
        }
    }
}
