package org.jellyfin.mobile.common.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

private const val HOME = "home"

private val backStackSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

/**
 * A back stack of pages opened over a home screen, e.g. home → artist → album. Only page names are
 * saved, so the stack survives rotation and process death, and each level keeps its own saved state
 * (a scroll position) while covered.
 */
@Stable
class VaultBackStack internal constructor(
    private val stack: SnapshotStateList<String>,
    private val savedStates: SaveableStateHolder,
) {
    /** The pages open over home, the bottom one first. */
    val pages: List<String> get() = stack

    fun open(page: String) {
        stack += page
    }

    /** Closes the page at [depth] (the first over home is 1) and any open over it. */
    fun closeFrom(depth: Int) {
        while (stack.size >= depth.coerceAtLeast(1)) {
            savedStates.removeState(keyAt(stack.size))
            stack.removeAt(stack.lastIndex)
        }
    }

    /**
     * The page at [depth]'s own key, which its saved state is kept under (and anything else of its
     * own, such as its view models). Keyed by depth too, so the same page opened at two depths keeps
     * separate state.
     */
    fun keyAt(depth: Int) = "$depth:${stack.getOrNull(depth - 1) ?: HOME}"

    /**
     * Shows the page at [depth] (home is 0; the top page by default) with its saved state, and back
     * closes it. [content] gets the page (null for home) and how to close it, which closes only that
     * page, however often it's called: a page can leave twice before it's redrawn (its back tapped as
     * its book stops), and mustn't close the page under it too.
     */
    @Composable
    fun Show(depth: Int = stack.size, content: @Composable (page: String?, close: () -> Unit) -> Unit) {
        val page = stack.getOrNull(depth - 1)
        val close = { if (depth > 0 && stack.getOrNull(depth - 1) == page) closeFrom(depth) }
        BackHandler(enabled = depth > 0, onBack = close)
        savedStates.SaveableStateProvider(keyAt(depth)) { content(page, close) }
    }
}

@Composable
fun rememberVaultBackStack(): VaultBackStack {
    val stack = rememberSaveable(saver = backStackSaver) { mutableStateListOf() }
    val savedStates = rememberSaveableStateHolder()
    return remember(stack, savedStates) { VaultBackStack(stack, savedStates) }
}
