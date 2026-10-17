package org.jellyfin.mobile.books.reader

import org.w3c.dom.Element
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Small namespace-tolerant DOM-walking helpers shared by [EpubRenderer] (container.xml/OPF) and
 * [EpubTocParser] (nav.xhtml/toc.ncx) — extracted so each parser stays focused on its own XML
 * dialect rather than re-implementing the same tag-matching/traversal logic.
 */
internal fun parseXml(file: File): Element {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        // EPUBs are untrusted, and NCX files name the DAISY DTD by URL: never fetch DTDs or
        // external entities. Android's parser never does and rejects these features, hence runCatching.
        for (feature in UNTRUSTED_XML_FEATURES) runCatching { setFeature(feature, false) }
    }
    val document = file.inputStream().use { factory.newDocumentBuilder().parse(it) }
    return document.documentElement
}

private val UNTRUSTED_XML_FEATURES = listOf(
    "http://apache.org/xml/features/nonvalidating/load-external-dtd",
    "http://xml.org/sax/features/external-general-entities",
    "http://xml.org/sax/features/external-parameter-entities",
)

/**
 * The file an EPUB `href` points to, relative to [baseDir]. Hrefs are URLs: drop any `#fragment`
 * and percent-decode (`chapter%201.xhtml` is the file `chapter 1.xhtml`); unlike form data, `+`
 * stays a plus.
 */
internal fun resolveEpubHref(baseDir: File, href: String): File {
    val path = href.substringBefore('#').replace("+", "%2B")
    return File(baseDir, URLDecoder.decode(path, Charsets.UTF_8.name()))
}

/** Whether this file is inside [dir], once any ".." and links in its path are resolved. */
internal fun File.isInside(dir: File): Boolean = try {
    canonicalPath.startsWith(dir.canonicalPath + File.separator)
} catch (_: IOException) {
    false
}

/**
 * XML parsing here can throw any of [org.xml.sax.SAXException], [java.io.IOException], or
 * [javax.xml.parsers.ParserConfigurationException] depending on what's malformed about a given
 * EPUB — all treated the same way (log and fall back to "unparseable").
 */
@Suppress("TooGenericExceptionCaught")
internal fun parseXmlOrNull(file: File): Element? = try {
    parseXml(file)
} catch (e: Exception) {
    Timber.e(e, "Failed to parse %s", file.name)
    null
}

/** Tag names may carry a namespace prefix (e.g. `opf:item`) — compare against the local part. */
internal fun Element.localTagMatches(name: String): Boolean = tagName.substringAfter(':') == name

internal fun Element.findDescendant(name: String): Element? {
    val elements = getElementsByTagName("*")
    for (i in 0 until elements.length) {
        val node = elements.item(i) as? Element ?: continue
        if (node.localTagMatches(name)) return node
    }
    return null
}

/** Like [findDescendant], but collects every matching descendant instead of just the first. */
internal fun Element.descendants(name: String): List<Element> {
    val result = mutableListOf<Element>()
    val elements = getElementsByTagName("*")
    for (i in 0 until elements.length) {
        val node = elements.item(i) as? Element ?: continue
        if (node.localTagMatches(name)) result += node
    }
    return result
}

internal fun Element.children(name: String): List<Element> {
    val result = mutableListOf<Element>()
    val nodes = childNodes
    for (i in 0 until nodes.length) {
        val node = nodes.item(i) as? Element ?: continue
        if (node.localTagMatches(name)) result += node
    }
    return result
}
