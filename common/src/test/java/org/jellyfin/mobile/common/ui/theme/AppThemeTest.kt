package org.jellyfin.mobile.common.ui.theme

import android.app.Application
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.contentColorFor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.common.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Compose theme and the XML theme (splash, window background, the screens still built from
 * layouts) have to agree, or an app shows two different blacks or two different accents.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AppThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun xmlColor(id: Int) = Color(ContextCompat.getColor(ApplicationProvider.getApplicationContext(), id))

    @Test
    fun `the app's brand is the Compose accent, over the shared neutrals`() {
        lateinit var colors: Colors
        composeRule.setContent {
            AppTheme(VaultBrand.BookVault) { colors = MaterialTheme.colors }
        }

        colors.primary shouldBe VaultBrand.BookVault.accent
        colors.secondary shouldBe VaultBrand.BookVault.accent
        colors.onPrimary shouldBe VaultBrand.BookVault.onAccent
        colors.background shouldBe VaultColors.Background
        colors.surface shouldBe VaultColors.Surface
    }

    @Test
    fun `bars, cards and screens get light content, not the label color meant for the accent`() {
        // contentColorFor matches primaryVariant before surface, so a primaryVariant equal to the
        // surface gave top bars and the bottom bar dark titles and icons
        var onSurface = Color.Unspecified
        var onBackground = Color.Unspecified
        composeRule.setContent {
            AppTheme(VaultBrand.TuneVault) {
                onSurface = contentColorFor(MaterialTheme.colors.surface)
                onBackground = contentColorFor(MaterialTheme.colors.background)
            }
        }

        onSurface shouldBe VaultColors.OnBackground
        onBackground shouldBe VaultColors.OnBackground
    }

    @Test
    fun `the XML neutrals match the Compose ones`() {
        xmlColor(R.color.theme_background).toArgb() shouldBe VaultColors.Background.toArgb()
        xmlColor(R.color.jellyfin_primary).toArgb() shouldBe VaultColors.Surface.toArgb()
    }

    @Test
    fun `each app's XML accent matches its Compose brand`() {
        xmlColor(R.color.vault_accent_audiovault).toArgb() shouldBe VaultBrand.AudioVault.accent.toArgb()
        xmlColor(R.color.vault_accent_bookvault).toArgb() shouldBe VaultBrand.BookVault.accent.toArgb()
        xmlColor(R.color.vault_accent_tunevault).toArgb() shouldBe VaultBrand.TuneVault.accent.toArgb()
        xmlColor(R.color.vault_accent_livevault).toArgb() shouldBe VaultBrand.LiveVault.accent.toArgb()
    }

    @Test
    fun `an app that sets no accent of its own gets Jellyfin blue`() {
        xmlColor(R.color.vault_accent).toArgb() shouldBe VaultBrand.Jellyfin.accent.toArgb()
    }
}
