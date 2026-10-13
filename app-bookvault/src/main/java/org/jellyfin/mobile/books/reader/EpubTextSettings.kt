package org.jellyfin.mobile.books.reader

import org.jellyfin.mobile.books.app.EpubFontFamily

private const val PERCENT_MULTIPLIER = 100

/**
 * Global EPUB text-appearance settings (font size/family/line spacing), persisted via
 * [org.jellyfin.mobile.books.app.BookAppPreferences]. Kept as a plain, pure data class so the CSS
 * it renders can be tested without a WebView.
 */
data class EpubTextSettings(
    val fontScale: Float = 1.0f,
    val fontFamily: EpubFontFamily = EpubFontFamily.SERIF,
    val lineSpacing: Float = 1.0f,
) {
    /** CSS declarations applied to `document.documentElement.style` — see `EpubChapterAdapter`. */
    fun toCssText(): String {
        val percent = (fontScale * PERCENT_MULTIPLIER).toInt()
        return "font-size:$percent%;font-family:${fontFamily.cssValue};line-height:$lineSpacing;"
    }
}
