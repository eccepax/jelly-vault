package org.jellyfin.mobile.books.reader

import org.jellyfin.mobile.books.app.EpubFontFamily

private const val PERCENT_MULTIPLIER = 100

/**
 * Discrete preset steps offered by the font-size picker (Settings screen and the reader's "Aa"
 * action). Each value is itself a meaningful preset, not an incidental magic number.
 */
@Suppress("MagicNumber")
val EPUB_FONT_SCALE_PRESETS: List<Float> = listOf(0.8f, 0.9f, 1.0f, 1.1f, 1.25f, 1.5f, 1.75f, 2.0f)

/** Discrete preset steps offered by the line-spacing picker. */
@Suppress("MagicNumber")
val EPUB_LINE_SPACING_PRESETS: List<Float> = listOf(1.0f, 1.2f, 1.5f, 1.8f, 2.0f)

/** All font families, in the order they're offered in the picker. */
val EPUB_FONT_FAMILY_PRESETS: List<EpubFontFamily> = EpubFontFamily.entries

fun formatFontScaleLabel(scale: Float): String = "${(scale * PERCENT_MULTIPLIER).toInt()}%"

fun formatLineSpacingLabel(spacing: Float): String = "${spacing}x"

fun formatFontFamilyLabel(family: EpubFontFamily): String = when (family) {
    EpubFontFamily.SERIF -> "Serif"
    EpubFontFamily.SANS_SERIF -> "Sans-serif"
    EpubFontFamily.MONOSPACE -> "Monospace"
}
