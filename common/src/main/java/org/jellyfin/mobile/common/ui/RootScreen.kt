package org.jellyfin.mobile.common.ui

import org.jellyfin.mobile.common.ServerState

/** The top-level screen an app shows, decided purely from persisted session state. */
enum class RootScreen {
    Splash,
    Connect,
    Login,
    SignedIn,
}

fun resolveRootScreen(serverState: ServerState, currentUserId: Long?): RootScreen = when (serverState) {
    ServerState.Pending -> RootScreen.Splash
    ServerState.Unset -> RootScreen.Connect
    is ServerState.Available -> if (currentUserId == null) RootScreen.Login else RootScreen.SignedIn
}
