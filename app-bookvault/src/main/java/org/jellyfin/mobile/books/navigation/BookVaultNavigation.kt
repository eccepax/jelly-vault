package org.jellyfin.mobile.books.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jellyfin.mobile.common.ui.RootScreen
import org.jellyfin.mobile.common.ui.navigation.VaultBackStack
import org.jellyfin.mobile.common.ui.navigation.rememberVaultBackStack

private const val READER = "reader:"
private const val SETTINGS = "settings"

/** What a screen can open on top of itself. */
class Navigator(
    val openBook: (bookId: String) -> Unit,
    val openSettings: () -> Unit,
)

/**
 * BookVault's back stack ([VaultBackStack], as AudioVault and TuneVault have): [home] (the library)
 * with a book's reader and the settings opened on top of it. Back pops one level.
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
    val backStack = rememberVaultBackStack()
    val viewModelStores: PageViewModelStores = viewModel { PageViewModelStores() }
    val navigator = remember(backStack) {
        // A second tap can land before the screen has changed: it opens nothing more
        fun open(page: String) {
            if (backStack.pages.lastOrNull() != page) backStack.open(page)
        }
        Navigator(
            openBook = { open(READER + it) },
            openSettings = { open(SETTINGS) },
        )
    }

    val signedIn = rootScreen == RootScreen.SignedIn
    LaunchedEffect(rootScreen) {
        if (!signedIn && rootScreen != RootScreen.Splash) backStack.closeFrom(1)
    }
    // What can show now: nothing over home, unless someone's signed in
    val showing = if (signedIn) backStack.pages.size else 0

    // Closed pages' view models go once the pages have: until then, they may still be drawing
    val open = (1..backStack.pages.size).map(backStack::keyAt).toSet()
    LaunchedEffect(open) { viewModelStores.keepOnly(open) }

    backStack.Show(depth = showing) { top, close ->
        if (top == null) {
            home(navigator)
        } else {
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides viewModelStores.owner(backStack.keyAt(showing)),
            ) {
                if (top == SETTINGS) settings(close) else reader(top.removePrefix(READER), close)
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
