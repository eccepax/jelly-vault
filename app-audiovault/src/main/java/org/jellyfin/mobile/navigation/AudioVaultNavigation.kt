package org.jellyfin.mobile.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import org.jellyfin.mobile.common.ui.RootScreen

private const val PLAYER = "player"
private const val DOWNLOADS = "downloads"
private const val SETTINGS = "settings"
private const val HOME = "home"

private val backStackSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

/** What a screen can open on top of itself. */
class Navigator(
    val openPlayer: () -> Unit,
    val openDownloads: () -> Unit,
    val openSettings: () -> Unit,
)

/**
 * AudioVault's back stack: [home] with the player, downloads and settings opened on top of it, e.g.
 * home → downloads → player. Back pops one level. Only names are saved, so the stack survives
 * rotation and process death, and each level keeps its own saved state (scroll position) while
 * covered. Modeled on TuneVault's LibraryNavigation.
 *
 * The settings belong to whoever's signed in ([rootScreen], as home shows it): they wait while
 * that's still being read (restored after the app's process went), and close once there's no one.
 * The downloads, and the player on them, work signed out too.
 */
@Composable
fun AudioVaultNavigation(
    rootScreen: RootScreen,
    home: @Composable (navigator: Navigator) -> Unit,
    player: @Composable (onBack: () -> Unit) -> Unit,
    downloads: @Composable (onBack: () -> Unit, navigator: Navigator) -> Unit,
    settings: @Composable (onBack: () -> Unit) -> Unit,
) {
    val stack = rememberSaveable(saver = backStackSaver) { mutableStateListOf() }
    val savedStates = rememberSaveableStateHolder()

    // Keyed by depth too, so the same page opened at two depths keeps separate state.
    fun keyAt(depth: Int) = "$depth:${stack.getOrNull(depth - 1) ?: HOME}"
    val pop: () -> Unit = {
        savedStates.removeState(keyAt(stack.size))
        stack.removeAt(stack.lastIndex)
    }

    // Opening what's already showing (the mini player tapped twice) mustn't stack it again
    fun open(page: String) {
        if (stack.lastOrNull() != page) stack += page
    }
    val navigator = Navigator(
        openPlayer = { open(PLAYER) },
        openDownloads = { open(DOWNLOADS) },
        openSettings = { open(SETTINGS) },
    )

    val signedIn = rootScreen == RootScreen.SignedIn
    LaunchedEffect(rootScreen) {
        if (!signedIn && rootScreen != RootScreen.Splash) {
            val settingsAt = stack.indexOf(SETTINGS)
            if (settingsAt >= 0) while (stack.size > settingsAt) pop()
        }
    }
    // What can show now: a prefix of the stack, so its keys are the stack's
    val showing = if (signedIn) stack else stack.takeWhile { it != SETTINGS }

    BackHandler(enabled = showing.isNotEmpty(), onBack = pop)

    savedStates.SaveableStateProvider(keyAt(showing.size)) {
        when (showing.lastOrNull()) {
            null -> home(navigator)
            PLAYER -> player(pop)
            DOWNLOADS -> downloads(pop, navigator)
            else -> settings(pop)
        }
    }
}
