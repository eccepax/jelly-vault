package org.jellyfin.mobile.player.session

import android.net.Uri
import androidx.media3.common.MediaItem

/**
 * Where the item plays from. A MediaController -> MediaSession hop strips an item's uri, so the
 * apps send it in requestMetadata too; this is whichever the item still has.
 */
val MediaItem.playableUri: Uri? get() = localConfiguration?.uri ?: requestMetadata.mediaUri

/** Session side: restores the stream uri from requestMetadata if the controller stripped it. */
fun MediaItem.withPlayableUri(): MediaItem {
    if (localConfiguration != null) return this
    val uri = requestMetadata.mediaUri ?: return this
    return buildUpon().setUri(uri).build()
}
