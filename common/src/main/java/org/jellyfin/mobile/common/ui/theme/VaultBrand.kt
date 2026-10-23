package org.jellyfin.mobile.common.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * What makes one Vault app look like itself: its accent, used for buttons, progress, selection
 * and links. Each icon's color is too dark to read on the dark background, so each accent is a
 * lighter tint of it. Labels on the accent take whichever of dark or white reads better.
 */
data class VaultBrand(val accent: Color) {
    val onAccent: Color =
        if (contrastRatio(VaultColors.Background, accent) >= contrastRatio(Color.White, accent)) {
            VaultColors.Background
        } else {
            Color.White
        }

    @Suppress("MagicNumber")
    companion object {
        val Jellyfin = VaultBrand(Color(0xFF00A4DC))

        /** Orange, from the hare icon. */
        val AudioVault = VaultBrand(Color(0xFFFF8C1A))

        /** Sea green, from the turtle icon. */
        val BookVault = VaultBrand(Color(0xFF16B08A))

        /** Red, from the woodpecker icon. */
        val TuneVault = VaultBrand(Color(0xFFF75F6D))

        /** Violet, from the frog icon. */
        val LiveVault = VaultBrand(Color(0xFFB470FF))
    }
}
