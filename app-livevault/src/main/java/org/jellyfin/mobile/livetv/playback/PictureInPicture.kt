package org.jellyfin.mobile.livetv.playback

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import org.jellyfin.mobile.livetv.app.isTelevision

/**
 * While [enabled], leaving the app (Home, or a gesture) shrinks it into a floating window instead
 * of stopping it, so a channel keeps playing. Not on a TV: there Home should leave, rather than
 * keep the stream (and the provider's one login) busy in a corner nobody's watching.
 */
@Composable
fun PictureInPictureOnLeave(enabled: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    if (!activity.supportsPictureInPicture()) return
    DisposableEffect(activity, enabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // The system enters it itself, with a smoother animation than entering on the leave hint
            activity.setPictureInPictureParams(pictureInPictureParams(autoEnter = enabled))
            onDispose {
                if (enabled) activity.setPictureInPictureParams(pictureInPictureParams(autoEnter = false))
            }
        } else {
            val enter = Runnable { if (enabled) activity.enterPictureInPictureMode(pictureInPictureParams()) }
            activity.addOnUserLeaveHintListener(enter)
            onDispose { activity.removeOnUserLeaveHintListener(enter) }
        }
    }
}

/** Whether the app is showing in the picture-in-picture window right now. */
@Composable
fun rememberInPictureInPicture(): Boolean {
    val activity = LocalActivity.current as? ComponentActivity ?: return false
    var inPictureInPicture by remember(activity) { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPictureInPicture = it.isInPictureInPictureMode }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPictureInPicture
}

private fun ComponentActivity.supportsPictureInPicture(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) && !isTelevision

private fun pictureInPictureParams(autoEnter: Boolean = false): PictureInPictureParams =
    PictureInPictureParams.Builder()
        .setAspectRatio(Rational(VIDEO_WIDTH, VIDEO_HEIGHT))
        .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(autoEnter) }
        .build()

private const val VIDEO_WIDTH = 16
private const val VIDEO_HEIGHT = 9
