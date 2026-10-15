package org.jellyfin.mobile.common.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * What makes one Vault app look like itself: its accent, used for buttons, progress, selection
 * and links. Each launcher color is too dark to read on the dark background, so each accent is a
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

        /** Crimson, from the launcher's #880015. */
        val AudioVault = VaultBrand(Color(0xFFFF5C72))

        /** Teal, from the launcher's #004D40. */
        val BookVault = VaultBrand(Color(0xFF26A69A))

        /** Indigo, from the launcher's #1A237E. */
        val TuneVault = VaultBrand(Color(0xFF7986CB))

        /** LiveVault's launcher is a near-black slate, so it keeps Jellyfin blue. */
        val LiveVault = Jellyfin
    }
}
