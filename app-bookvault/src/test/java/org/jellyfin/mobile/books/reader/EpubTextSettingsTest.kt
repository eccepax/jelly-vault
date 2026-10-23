package org.jellyfin.mobile.books.reader

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jellyfin.mobile.books.app.EpubFontFamily
import org.junit.jupiter.api.Test

/**
 * Pure-function coverage for [EpubTextSettings.toCssText] — kept independent of WebView/Robolectric
 * so the CSS the reader injects (see `EpubChapterAdapter`) is testable directly.
 */
class EpubTextSettingsTest {

    @Test
    fun `default settings render 100 percent serif normal line-height`() {
        EpubTextSettings().toCssText() shouldBe "font-size:100%;font-family:serif;line-height:1.0;"
    }

    @Test
    fun `font scale is rendered as a percentage`() {
        EpubTextSettings(fontScale = 1.5f).toCssText() shouldContain "font-size:150%"
    }

    @Test
    fun `each font family maps to its CSS generic keyword`() {
        EpubTextSettings(fontFamily = EpubFontFamily.SANS_SERIF).toCssText() shouldContain "font-family:sans-serif"
        EpubTextSettings(fontFamily = EpubFontFamily.MONOSPACE).toCssText() shouldContain "font-family:monospace"
        EpubTextSettings(fontFamily = EpubFontFamily.SERIF).toCssText() shouldContain "font-family:serif"
    }

    @Test
    fun `line spacing is rendered as the line-height value`() {
        EpubTextSettings(lineSpacing = 1.8f).toCssText() shouldContain "line-height:1.8"
    }
}
