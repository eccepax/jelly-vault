package org.jellyfin.mobile.common.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private const val NOTIFICATION_PERMISSION_SDK = 33

/**
 * Whether to ask to show notifications: on Android 13+ download progress needs it (playback's
 * media notification doesn't). Asked once, when a download starts and the reason is plain; never
 * again, so downloading never nags.
 */
fun shouldAskForNotifications(sdkInt: Int, granted: Boolean, alreadyAsked: Boolean): Boolean =
    sdkInt >= NOTIFICATION_PERMISSION_SDK && !granted && !alreadyAsked

/**
 * Returns an action to run as a download starts: it asks for notifications, the first time only.
 * The app keeps whether it has asked: [alreadyAsked] reads it, [markAsked] saves it.
 */
@Composable
fun rememberAskForNotificationsOnce(alreadyAsked: () -> Boolean, markAsked: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        val granted = Build.VERSION.SDK_INT < NOTIFICATION_PERMISSION_SDK ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (shouldAskForNotifications(Build.VERSION.SDK_INT, granted, alreadyAsked())) {
            markAsked()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
