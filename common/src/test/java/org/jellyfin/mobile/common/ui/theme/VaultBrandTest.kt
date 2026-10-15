package org.jellyfin.mobile.common.ui.theme

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual

/**
 * Each app picks its own accent, but every accent has to stay readable: as text and icons on the
 * dark background, and as a button behind its own label (WCAG AA, 4.5:1).
 */
class VaultBrandTest : FunSpec({
    val brands = mapOf(
        "AudioVault" to VaultBrand.AudioVault,
        "BookVault" to VaultBrand.BookVault,
        "TuneVault" to VaultBrand.TuneVault,
        "LiveVault" to VaultBrand.LiveVault,
    )

    brands.forEach { (app, brand) ->
        test("$app's accent is readable on the background") {
            contrastRatio(brand.accent, VaultColors.Background) shouldBeGreaterThanOrEqual 4.5
        }

        test("$app's button labels are readable on its accent") {
            contrastRatio(brand.onAccent, brand.accent) shouldBeGreaterThanOrEqual 4.5
        }
    }
})
