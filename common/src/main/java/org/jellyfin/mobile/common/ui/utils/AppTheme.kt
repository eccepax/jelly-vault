package org.jellyfin.mobile.common.ui.utils

import androidx.compose.runtime.Composable
import org.jellyfin.mobile.common.ui.theme.VaultBrand
import org.jellyfin.mobile.common.ui.theme.AppTheme as VaultAppTheme

/**
 * The theme before each app had its own brand. Only AudioVault still calls it; it moves to
 * `AppTheme(VaultBrand.AudioVault)` when its screens move to Compose.
 */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    VaultAppTheme(VaultBrand.Jellyfin, content)
}
