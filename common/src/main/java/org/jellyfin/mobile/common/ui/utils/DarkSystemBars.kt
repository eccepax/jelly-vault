package org.jellyfin.mobile.common.ui.utils

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/**
 * Draws edge to edge with dark system bars and light icons, whatever the phone's own theme: the
 * apps are always dark. Left to "auto", a phone in light mode with 3-button navigation put a
 * light-gray bar with dark buttons under the app.
 */
fun ComponentActivity.enableDarkEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )
}
