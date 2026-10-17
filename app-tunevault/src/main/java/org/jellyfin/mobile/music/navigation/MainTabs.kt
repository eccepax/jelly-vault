package org.jellyfin.mobile.music.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.jellyfin.mobile.common.ui.components.VaultBottomBar
import org.jellyfin.mobile.common.ui.components.VaultBottomBarItem

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
    VaultBottomBar {
        MainTab.entries.forEach { tab ->
            VaultBottomBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = tab.icon,
                label = stringResource(tab.label),
            )
        }
    }
}
