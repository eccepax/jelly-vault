package org.jellyfin.mobile.books.reader

import java.io.File
import java.io.IOException
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
    val headEnd = html.lastIndexOf("</head>", ignoreCase = true)
    return if (headEnd < 0) style + html else html.substring(0, headEnd) + style + html.substring(headEnd)
}

/**
 * Whether the reader may load [url] for a book extracted to [bookDir]: only plain files inside that
 * folder. EPUBs are untrusted, and the app's other files (the server sign-in among them) sit next
 * to the book's; so does the network.
 */
fun isReaderRequestAllowed(url: String, bookDir: File): Boolean {
    val file = plainFileOf(url) ?: return false
    return try {
        file.canonicalPath.startsWith(bookDir.canonicalPath + File.separator)
    } catch (_: IOException) {
        false
    }
}

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

private const val NIGHT_FILTER = "filter:invert(1) hue-rotate(180deg)"
