package org.jellyfin.mobile.books.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jellyfin.mobile.common.ui.RootScreen

private const val READER = "reader:"
private const val SETTINGS = "settings"
private const val HOME = "home"

private val backStackSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)

/** What a screen can open on top of itself. */
class Navigator(
    val openBook: (bookId: String) -> Unit,
    val openSettings: () -> Unit,
)

/**
 * BookVault's back stack: [home] (the library) with a book's reader and the settings opened on top
 * of it. Back pops one level. Only names and ids are saved, so the stack survives rotation and
 * process death, and each level keeps its own saved state (scroll position) while covered.
 * Modeled on AudioVault's AudioVaultNavigation.
 *
 * A book's reader and the settings get view models of their own, kept across rotation and let go
 * once closed: an open book holds its file, so it mustn't stay open behind the library, and the
 * settings watch the space downloads take, which is no use once they're closed.
 *
 * Everything over home belongs to whoever's signed in ([rootScreen], as home shows it): it waits
 * while that's still being read (restored after the app's process went), and closes once there's
 * no one.
 */
@Composable
fun BookVaultNavigation(
    rootScreen: RootScreen,
    home: @Composable (navigator: Navigator) -> Unit,
    reader: @Composable (bookId: String, onBack: () -> Unit) -> Unit,
    settings: @Composable (onBack: () -> Unit) -> Unit,
) {
    val stack = rememberSaveable(saver = backStackSaver) { mutableStateListOf() }
    val savedStates = rememberSaveableStateHolder()
    val viewModelStores: PageViewModelStores = viewModel { PageViewModelStores() }

    // Keyed by depth too, so the same page opened at two depths keeps separate state.
    fun keyAt(depth: Int) = "$depth:${stack.getOrNull(depth - 1) ?: HOME}"
    // A second back, or a second tap, can land before the screen has changed: each acts once
    val pop: () -> Unit = pop@{
        if (stack.isEmpty()) return@pop
        val key = keyAt(stack.size)
        savedStates.removeState(key)
        stack.removeAt(stack.lastIndex)
    }
    fun open(page: String) {
        if (stack.lastOrNull() != page) stack += page
    }
    val navigator = Navigator(
        openBook = { open(READER + it) },
        openSettings = { open(SETTINGS) },
    )

    val signedIn = rootScreen == RootScreen.SignedIn
    LaunchedEffect(rootScreen) {
        if (!signedIn && rootScreen != RootScreen.Splash) while (stack.isNotEmpty()) pop()
    }
    // What can show now: a prefix of the stack, so its keys are the stack's
    val showing = if (signedIn) stack else emptyList()

    BackHandler(enabled = showing.isNotEmpty(), onBack = pop)

    // Closed pages' view models go once the pages have: until then, they may still be drawing
    val open = (1..stack.size).map(::keyAt).toSet()
    LaunchedEffect(open) { viewModelStores.keepOnly(open) }

    val key = keyAt(showing.size)
    val top = showing.lastOrNull()
    savedStates.SaveableStateProvider(key) {
        if (top == null) {
            home(navigator)
        } else {
            CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStores.owner(key)) {
                if (top == SETTINGS) settings(pop) else reader(top.removePrefix(READER), pop)
            }
        }
    }
}

/** The view models of the pages open, each page's in a store of its own that goes when it's closed. */
private class PageViewModelStores : ViewModel() {
    private val owners = mutableMapOf<String, ViewModelStoreOwner>()

    fun owner(key: String): ViewModelStoreOwner = owners.getOrPut(key) {
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }
    }

    fun keepOnly(keys: Set<String>) {
        val closed = owners.keys - keys
        closed.forEach { owners.remove(it)?.viewModelStore?.clear() }
    }

    override fun onCleared() {
        owners.values.forEach { it.viewModelStore.clear() }
        owners.clear()
    }
}
