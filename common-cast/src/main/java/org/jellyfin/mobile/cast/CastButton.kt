package org.jellyfin.mobile.cast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * The standard cast button: it shows only while a cast device is in reach, and opens the
 * framework's own device picker (or, while casting, its stop dialog). Nothing where casting can't
 * work. The activity must be a FragmentActivity, which hosts those dialogs.
 */
@Composable
fun CastButton(casting: Casting, modifier: Modifier = Modifier) {
    val available by casting.isAvailable.collectAsState()
    if (!available) return
    AndroidView(
        factory = { context ->
            MediaRouteButton(context).also { CastButtonFactory.setUpMediaRouteButton(context, it) }
        },
        modifier = modifier,
    )
}
