package org.jellyfin.mobile.common.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jellyfin.mobile.common.ui.theme.VaultAlpha

/**
 * A screen's title bar, flat on the background like the rest of the screen: no separate band of
 * color, no shadow. The screen is in charge of the status bar inset.
 */
@Composable
fun VaultTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        backgroundColor = MaterialTheme.colors.background,
        contentColor = MaterialTheme.colors.onBackground,
        elevation = 0.dp,
    )
}

/** The tabs along the bottom of an app, over the navigation bar; fill it with [VaultBottomBarItem]s. */
@Composable
fun VaultBottomBar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    // Opaque under the hairline too, so it blends to the same grey over whatever is behind the bar
    Column(modifier = modifier.background(MaterialTheme.colors.background)) {
        // On the same background as the screen, a hairline is what sets the tabs apart from it
        Divider(color = MaterialTheme.colors.onBackground.copy(alpha = DIVIDER_ALPHA))
        BottomNavigation(
            windowInsets = WindowInsets.navigationBars,
            backgroundColor = MaterialTheme.colors.background,
            contentColor = MaterialTheme.colors.onBackground,
            elevation = 0.dp,
            content = content,
        )
    }
}

/** A tab in [VaultBottomBar], in the app's accent while it's selected. */
@Composable
fun RowScope.VaultBottomBarItem(selected: Boolean, onClick: () -> Unit, icon: ImageVector, label: String) {
    BottomNavigationItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selectedContentColor = MaterialTheme.colors.primary,
        unselectedContentColor = MaterialTheme.colors.onBackground.copy(alpha = VaultAlpha.SECONDARY),
    )
}

private const val DIVIDER_ALPHA = 0.12f
