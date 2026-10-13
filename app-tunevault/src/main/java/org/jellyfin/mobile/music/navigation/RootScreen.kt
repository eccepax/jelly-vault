package org.jellyfin.mobile.music.navigation

import org.jellyfin.mobile.common.ServerState

/** The top-level screen TuneVault shows, decided purely from persisted session state. */
enum class RootScreen {
    Splash,
    Connect,
    Login,
    Library,
}

fun resolveRootScreen(serverState: ServerState, currentUserId: Long?): RootScreen = when (serverState) {
    ServerState.Pending -> RootScreen.Splash
    ServerState.Unset -> RootScreen.Connect
    is ServerState.Available -> if (currentUserId == null) RootScreen.Login else RootScreen.Library
}
