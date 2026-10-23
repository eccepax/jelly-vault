package org.jellyfin.mobile.books.reader

import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.InvalidPathException
import java.nio.file.Paths

/**
 * [html] with the reader's [textCss] (and night mode's inversion) as a stylesheet at the end of its
 * head, so they apply without scripts. `!important` keeps them over the book's own styles, as the
 * inline style they replace did.
 */
fun styledChapterHtml(html: String, textCss: String, night: Boolean): String {
    val declarations = textCss.split(';').map(String::trim).filter(String::isNotEmpty) +
        listOfNotNull(NIGHT_FILTER.takeIf { night })
    val style = "<style>html{${declarations.joinToString(";") { "$it !important" }}}</style>"
    // At the end of the head; without one, just inside <html>, so it never lands before a doctype
    // (which would put the page in quirks mode)
    val at = HEAD_END.find(html)?.range?.first
        ?: HTML_START.find(html)?.let { it.range.last + 1 }
        ?: 0
    return html.substring(0, at) + style + html.substring(at)
}

/**
 * The address a chapter [file] is loaded at, opening at the element [anchor] names, if any. Each
 * [load] gets an address of its own: loading the address already showing, with an anchor, would
 * only scroll to it, leaving the page as it was styled before (text settings, night mode).
 */
fun chapterUrl(file: File, anchor: String?, load: Int): String =
    URI("file", "", file.toURI().path, "load=$load", anchor).toASCIIString()

/** Whether a page the reader opens is a chapter (restyled), rather than a picture or such it links to (shown as it is). */
fun isChapterDocument(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in CHAPTER_EXTENSIONS

private val CHAPTER_EXTENSIONS = setOf("xhtml", "html", "htm", "xml")

/**
 * Whether the reader may load [url] for a book extracted to [bookDir]: only plain files inside that
 * folder, and pictures or fonts written into the page itself (`data:`). EPUBs are untrusted, and the
 * app's other files (the server sign-in among them) sit next to the book's; so does the network.
 */
fun isReaderRequestAllowed(url: String, bookDir: File): Boolean =
    // A picture or font written into the chapter itself brings nothing in and takes nothing out
    url.startsWith("data:", ignoreCase = true) || plainFileOf(url)?.isInside(bookDir) == true

/** The file [url] names, if it's a plain file URL. */
private fun plainFileOf(url: String): File? = try {
    val uri = URI(url)
    if (uri.scheme.equals("file", ignoreCase = true) && uri.path != null) {
        // Through the platform's own path type, so "%2e%2e" and ".." are resolved before checking
        Paths.get(URI("file", null, uri.path, null)).toFile()
    } else {
        null
    }
} catch (_: URISyntaxException) {
    null
} catch (_: IllegalArgumentException) {
    null
} catch (_: InvalidPathException) {
    null
}

private val HEAD_END = Regex("""</head\s*>""", RegexOption.IGNORE_CASE)
private val HTML_START = Regex("""<html\b[^>]*>""", RegexOption.IGNORE_CASE)
private const val NIGHT_FILTER = "filter:invert(1) hue-rotate(180deg)"
