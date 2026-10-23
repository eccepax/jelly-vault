package org.jellyfin.mobile.books.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.library.BookLibraryRoute
import org.jellyfin.mobile.books.reader.BookReaderRoute
import org.jellyfin.mobile.books.settings.BookSettingsRoute
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.ui.VaultRoot
import org.jellyfin.mobile.common.ui.resolveRootScreen
import org.jellyfin.mobile.common.ui.startOver

/** BookVault: connect to a server and sign in, then the library, with a book's reader and the settings opened over it. */
@Composable
fun BookVaultRoot(mainViewModel: MainViewModel, appPreferences: AppPreferences) {
    val activity = LocalActivity.current
    // Worked out as VaultRoot does, which shows the matching screen at home
    val serverState by mainViewModel.serverState.collectAsState()
    val rootScreen = resolveRootScreen(serverState, appPreferences.currentUserId)
    BookVaultNavigation(
        rootScreen = rootScreen,
        home = { navigator ->
            VaultRoot(mainViewModel, appPreferences, logoRes = R.drawable.book_logo) {
                BookLibraryRoute(onOpenBook = navigator.openBook, onOpenSettings = navigator.openSettings)
            }
        },
        reader = { bookId, onBack -> BookReaderRoute(bookId = bookId, onBack = onBack) },
        settings = { onBack -> BookSettingsRoute(onBack = onBack, onSignedOut = { activity?.startOver() }) },
    )
}
