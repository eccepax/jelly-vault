package org.jellyfin.mobile.books.reader

import org.w3c.dom.Element
import timber.log.Timber
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Small namespace-tolerant DOM-walking helpers shared by [EpubRenderer] (container.xml/OPF) and
 * [EpubTocParser] (nav.xhtml/toc.ncx) — extracted so each parser stays focused on its own XML
 * dialect rather than re-implementing the same tag-matching/traversal logic.
 */
internal fun parseXml(file: File): Element {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
    }
    val document = file.inputStream().use { factory.newDocumentBuilder().parse(it) }
    return document.documentElement
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
