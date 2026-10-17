package org.jellyfin.mobile.common.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp

/** The theme every Vault screen is drawn in: the shared dark neutrals, in the app's own [brand]. */
@Composable
fun AppTheme(brand: VaultBrand, content: @Composable () -> Unit) {
    val colors = remember(brand) {
        darkColors(
            primary = brand.accent,
            // Never the surface: contentColorFor matches primaryVariant first, which would give
            // everything on a surface the label color meant for the accent
            primaryVariant = brand.accent,
            secondary = brand.accent,
            secondaryVariant = brand.accent,
            background = VaultColors.Background,
            surface = VaultColors.Surface,
            error = VaultColors.Error,
            onPrimary = brand.onAccent,
            onSecondary = brand.onAccent,
            onBackground = VaultColors.OnBackground,
            onSurface = VaultColors.OnBackground,
            onError = VaultColors.Background,
        )
    }
    MaterialTheme(colors = colors, shapes = VaultShapes, content = content)
}

private val VaultShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(0.dp),
)
