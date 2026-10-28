package org.jellyfin.mobile.livetv.playback

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import org.jellyfin.mobile.common.ui.rememberAskForNotificationsOnce

/**
 * Asks for notifications the first time a channel plays on a cast device ([casting]): its
 * notification (see [LiveCastService]) is what says where it plays, and ends the cast, from outside
 * the app. Asked once, as the other apps do for downloads.
 */
@Composable
fun AskForNotificationsWhenCasting(casting: Boolean) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences(FILE, Context.MODE_PRIVATE) }
    val ask = rememberAskForNotificationsOnce(
        alreadyAsked = { preferences.getBoolean(KEY_ASKED, false) },
        markAsked = { preferences.edit { putBoolean(KEY_ASKED, true) } },
    )
    LaunchedEffect(casting) {
        if (casting) ask()
    }
}

private const val FILE = "cast_notifications"
private const val KEY_ASKED = "asked"
