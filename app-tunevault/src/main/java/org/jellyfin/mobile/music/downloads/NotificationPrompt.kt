package org.jellyfin.mobile.music.downloads

import androidx.compose.runtime.Composable
import org.jellyfin.mobile.music.app.TuneAppPreferences
import org.koin.compose.koinInject
import org.jellyfin.mobile.common.ui.rememberAskForNotificationsOnce as rememberAskOnce

/** Returns an action to run as a download starts: it asks for notifications, the first time only. */
@Composable
fun rememberAskForNotificationsOnce(preferences: TuneAppPreferences = koinInject()): () -> Unit =
    rememberAskOnce(
        alreadyAsked = { preferences.askedForNotifications },
        markAsked = { preferences.askedForNotifications = true },
    )
