package org.jellyfin.mobile.common.ui.theme

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import org.junit.jupiter.api.Test

/**
 * Each app picks its own accent, but every accent has to stay readable: as text and icons on the
 * dark background, and as a button behind its own label (WCAG AA, 4.5:1).
 */
class VaultBrandTest {

    private val brands = mapOf(
        "AudioVault" to VaultBrand.AudioVault,
        "BookVault" to VaultBrand.BookVault,
        "TuneVault" to VaultBrand.TuneVault,
        "LiveVault" to VaultBrand.LiveVault,
    )

    @Test
    fun `every app's accent is readable on the background`() = assertSoftly {
        brands.forEach { (app, brand) ->
            withClue(app) {
                contrastRatio(brand.accent, VaultColors.Background) shouldBeGreaterThanOrEqual MIN_CONTRAST
            }
        }
    }

    @Test
    fun `every app's button labels are readable on its accent`() = assertSoftly {
        brands.forEach { (app, brand) ->
            withClue(app) {
                contrastRatio(brand.onAccent, brand.accent) shouldBeGreaterThanOrEqual MIN_CONTRAST
            }
        }
    }

    private companion object {
        const val MIN_CONTRAST = 4.5
    }
}
