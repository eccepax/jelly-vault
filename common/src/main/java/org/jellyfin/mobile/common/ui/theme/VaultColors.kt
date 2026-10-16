package org.jellyfin.mobile.common.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The neutrals every app shares; only the accent ([VaultBrand]) differs between apps. The XML
 * side keeps the same values in common's colors.xml, which AppThemeTest checks.
 */
@Suppress("MagicNumber")
object VaultColors {
    /** Behind everything, top and bottom bars included, so a bar never reads as a separate band. */
    val Background = Color(0xFF101010)

    /** Cards, rows and cover placeholders. */
    val Surface = Color(0xFF202020)

    /** Something raised over a [Surface]: menus, dialogs, the mini player. */
    val SurfaceElevated = Color(0xFF2C2C2C)

    val OnBackground = Color.White
    val Error = Color(0xFFCF6679)

    /** A favorite's star, the same gold in every app. */
    val Favorite = Color(0xFFFFC107)
}

/** How far secondary content and scrims fade, so every app dims by the same amount. */
object VaultAlpha {
    /** Secondary text and icons: subtitles, timestamps, an artist under an album. */
    const val SECONDARY = 0.7f

    /** A scrim laid over artwork or video so text on top of it stays readable. */
    const val SCRIM = 0.7f

    /** The hairline that sets a bar apart from a screen of the same background. */
    const val DIVIDER = 0.12f
}

/** The WCAG contrast ratio between two colors, from 1 (none) to 21 (black on white). */
@Suppress("MagicNumber")
fun contrastRatio(first: Color, second: Color): Double {
    val lighter = maxOf(first.luminance(), second.luminance())
    val darker = minOf(first.luminance(), second.luminance())
    return (lighter + 0.05) / (darker + 0.05)
}
