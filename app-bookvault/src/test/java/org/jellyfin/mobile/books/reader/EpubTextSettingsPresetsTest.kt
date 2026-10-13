package org.jellyfin.mobile.books.reader

import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.books.app.EpubFontFamily
import org.junit.jupiter.api.Test

/**
 * Pure-function coverage for the label formatting shared by the Settings-screen and in-reader
 * "Aa" text-settings pickers (both build their `AlertDialog` choice lists from these), so the two
 * surfaces can never drift out of sync on wording.
 */
class EpubTextSettingsPresetsTest {

    @Test
    fun `formatFontScaleLabel renders a whole percentage`() {
        formatFontScaleLabel(1.0f) shouldBe "100%"
        formatFontScaleLabel(0.8f) shouldBe "80%"
        formatFontScaleLabel(1.25f) shouldBe "125%"
    }

    @Test
    fun `formatLineSpacingLabel renders a multiplier`() {
        formatLineSpacingLabel(1.0f) shouldBe "1.0x"
        formatLineSpacingLabel(1.8f) shouldBe "1.8x"
    }

    @Test
    fun `formatFontFamilyLabel renders a human-readable name for every family`() {
        formatFontFamilyLabel(EpubFontFamily.SERIF) shouldBe "Serif"
        formatFontFamilyLabel(EpubFontFamily.SANS_SERIF) shouldBe "Sans-serif"
        formatFontFamilyLabel(EpubFontFamily.MONOSPACE) shouldBe "Monospace"
    }

    @Test
    fun `preset lists contain the current default value`() {
        (1.0f in EPUB_FONT_SCALE_PRESETS) shouldBe true
        (1.0f in EPUB_LINE_SPACING_PRESETS) shouldBe true
        (EpubFontFamily.SERIF in EPUB_FONT_FAMILY_PRESETS) shouldBe true
    }
}
