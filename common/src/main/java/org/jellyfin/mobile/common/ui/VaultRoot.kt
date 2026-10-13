package org.jellyfin.mobile.common.ui

import android.app.Activity
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.compose.AndroidFragment
import org.jellyfin.mobile.common.MainViewModel
import org.jellyfin.mobile.common.app.AppPreferences
import org.jellyfin.mobile.common.setup.LoginFragment
import org.jellyfin.mobile.common.ui.screens.connect.ConnectScreen

/**
 * The top of a Compose app: connect to a server, sign in, then [signedIn]. Its activity must be a
 * FragmentActivity (the sign-in form is common's LoginFragment) and must pass the activity-scoped
 * [mainViewModel], the same instance LoginFragment gets from activityViewModel().
 */
@Composable
fun VaultRoot(
    mainViewModel: MainViewModel,
    appPreferences: AppPreferences,
    @DrawableRes logoRes: Int,
    signedIn: @Composable () -> Unit,
) {
    val serverState by mainViewModel.serverState.collectAsState()
    // Re-resolved on every serverState emission: LoginFragment persists the user, then calls
    // MainViewModel.refreshState(), which emits a fresh ServerState.Available.
    when (resolveRootScreen(serverState, appPreferences.currentUserId)) {
        RootScreen.Splash -> Unit
        RootScreen.Connect -> ConnectScreen(
            mainViewModel = mainViewModel,
            showExternalConnectionError = false,
            logoRes = logoRes,
        )
        RootScreen.Login -> AndroidFragment<LoginFragment>(Modifier.fillMaxSize().systemBarsPadding())
        RootScreen.SignedIn -> signedIn()
    }
}

/**
 * Starts the app over in a fresh task after signing out. Finishing this activity clears every
 * ViewModel, so nothing the last account loaded survives into the next one's session.
 */
fun Activity.startOver() {
    startActivity(
        Intent(this, this::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
    )
}
