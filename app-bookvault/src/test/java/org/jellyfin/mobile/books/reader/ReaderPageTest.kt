package org.jellyfin.mobile.books.reader

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI

/**
 * An EPUB is untrusted: anyone can make one. The reader shows a chapter with scripts off and lets
 * it load only the book's own files, so a booby-trapped book can't read the app's other files
 * (where the server sign-in is kept) or send anything anywhere.
 */
class ReaderPageTest {

    @TempDir
    lateinit var cache: File

    private val book get() = File(cache, "epub_1").apply { mkdirs() }

    private fun fileUrl(file: File) = file.toURI().toString()

    @Test
    fun `the chapter can load the book's own files, in any of its folders`() {
        isReaderRequestAllowed(fileUrl(File(book, "OEBPS/images/cover.jpg")), book) shouldBe true
        isReaderRequestAllowed(fileUrl(File(book, "style.css")), book) shouldBe true
    }

    @Test
    fun `nothing from the network`() {
        isReaderRequestAllowed("https://example.com/steal?token=1", book) shouldBe false
        isReaderRequestAllowed("http://192.168.1.2/pixel.png", book) shouldBe false
    }

    @Test
    fun `none of the app's other files`() {
        val database = File(cache, "../databases/jellyfin.db")
        isReaderRequestAllowed(fileUrl(database), book) shouldBe false
    }

    @Test
    fun `no climbing out of the book's folder`() {
        isReaderRequestAllowed("${fileUrl(book)}/OEBPS/../../../databases/jellyfin.db", book) shouldBe false
        isReaderRequestAllowed("${fileUrl(book)}/OEBPS/%2e%2e/%2e%2e/%2e%2e/databases/jellyfin.db", book) shouldBe false
    }

    @Test
    fun `not another book that only starts with the same name`() {
        val otherBook = File(cache, "epub_10").apply { mkdirs() }
        isReaderRequestAllowed(fileUrl(File(otherBook, "chapter.xhtml")), book) shouldBe false
    }

    @Test
    fun `nothing that isn't a plain file link`() {
        isReaderRequestAllowed("content://com.android.contacts/contacts", book) shouldBe false
        isReaderRequestAllowed("not a url at all", book) shouldBe false
    }

    @Test
    fun `pictures and fonts written into the chapter itself load`() {
        isReaderRequestAllowed("data:image/png;base64,iVBORw0KGgo=", book) shouldBe true
        isReaderRequestAllowed("DATA:image/svg+xml,%3Csvg%2F%3E", book) shouldBe true
    }

    @Test
    fun `the reader's text settings come with the page, after the book's own styles`() {
        val html = """<html><head><link rel="stylesheet" href="book.css"/></head><body>Hi</body></html>"""

        val styled = styledChapterHtml(html, textCss = "font-size:150%;line-height:1.5;", night = false)

        styled shouldContain "font-size:150% !important"
        styled shouldContain "line-height:1.5 !important"
        styled.indexOf("<style") shouldBeAfter styled.indexOf("book.css")
        styled.indexOf("<style") shouldBeBefore styled.indexOf("</head>")
        styled shouldNotContain "invert"
    }

    @Test
    fun `night mode inverts the page`() {
        val styled = styledChapterHtml("<html><head></head><body/></html>", textCss = "", night = true)

        styled shouldContain "filter:invert(1) hue-rotate(180deg) !important"
    }

    @Test
    fun `a chapter without a head still gets the styles`() {
        val styled = styledChapterHtml("<p>Just text</p>", textCss = "font-size:100%;", night = false)

        styled shouldStartWith "<style"
        styled shouldContain "<p>Just text</p>"
    }

    @Test
    fun `an upper-case HEAD is found too`() {
        val styled = styledChapterHtml("<HTML><HEAD><TITLE>x</TITLE></HEAD><BODY/></HTML>", "font-size:100%;", false)

        styled.indexOf("<style") shouldBeAfter styled.indexOf("<TITLE>")
        styled.indexOf("<style") shouldBeBefore styled.indexOf("</HEAD>")
    }

    @Test
    fun `a closing head tag later on, in a comment say, doesn't move the styles`() {
        val html = "<html><head><title>x</title></head><body><!-- </head> --><p>Hi</p></body></html>"

        val styled = styledChapterHtml(html, "font-size:100%;", false)

        styled.indexOf("<style") shouldBeBefore styled.indexOf("<body>")
    }

    @Test
    fun `a closing head tag with a space before its bracket is found too`() {
        val styled = styledChapterHtml("<html><head><title>x</title></head ><body/></html>", "font-size:100%;", false)

        styled.indexOf("<style") shouldBeAfter styled.indexOf("<title>")
        styled.indexOf("<style") shouldBeBefore styled.indexOf("</head >")
    }

    @Test
    fun `without a closing head tag the styles still go after the doctype, so the page isn't in quirks mode`() {
        val html = """<?xml version="1.0"?><!DOCTYPE html><html xmlns="http://www.w3.org/1999/xhtml"><head/><body/></html>"""

        val styled = styledChapterHtml(html, "font-size:100%;", false)

        styled shouldStartWith "<?xml"
        styled.indexOf("<style") shouldBeAfter styled.indexOf("<html")
    }

    private infix fun Int.shouldBeAfter(other: Int) = (this > other && other >= 0) shouldBe true
    private infix fun Int.shouldBeBefore(other: Int) = (this in 0 until other) shouldBe true

    @Test
    fun `each load of a chapter has an address of its own, so it's read and styled afresh`() {
        val chapter = File(book, "OEBPS/ch1.xhtml")

        val first = chapterUrl(chapter, "ch 2", load = 1)
        val again = chapterUrl(chapter, "ch 2", load = 2)

        first shouldNotBe again
        URI(first).path shouldBe chapter.toURI().path
        URI(first).fragment shouldBe "ch 2"
        isReaderRequestAllowed(first, book) shouldBe true
    }

    @Test
    fun `a chapter opened at its start has no anchor`() {
        URI(chapterUrl(File(book, "ch1.xhtml"), anchor = null, load = 1)).fragment shouldBe null
    }
}
