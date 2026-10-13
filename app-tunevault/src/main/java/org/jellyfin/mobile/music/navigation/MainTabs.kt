package org.jellyfin.mobile.music.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * The selected tab's content. Each tab keeps its saved state (scroll positions, its own back stack)
 * while another is shown. Back from any other tab returns to [MainTab.START] (Home); back there is
 * left to the system. A tab's own back handling (an open album) still comes first: it registers later.
 */
@Composable
fun MainTabs(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    home: @Composable () -> Unit,
    library: @Composable () -> Unit,
    search: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabStates = rememberSaveableStateHolder()
    BackHandler(enabled = selected != MainTab.START) { onSelect(MainTab.START) }
    Box(modifier = modifier) {
        tabStates.SaveableStateProvider(selected.name) {
            when (selected) {
                MainTab.HOME -> home()
                MainTab.LIBRARY -> library()
                MainTab.SEARCH -> search()
            }
        }
    }
}

@Composable
fun MainBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    BottomNavigation(backgroundColor = MaterialTheme.colors.surface, windowInsets = WindowInsets.navigationBars) {
        MainTab.entries.forEach { tab ->
            BottomNavigationItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}
