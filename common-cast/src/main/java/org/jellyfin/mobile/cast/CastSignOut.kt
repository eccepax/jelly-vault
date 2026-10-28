package org.jellyfin.mobile.cast

/**
 * What signing out does about casting, in every app: [stopPlayback] first, so its last report goes
 * while the token still works and the queue is still there to report from, then any cast ends and
 * is forgotten ([forgetCast], see [Casting.forgetCast]) even if stopping failed, since the cast
 * device holds stream urls with the token in them.
 */
suspend fun stopPlaybackThenForgetCast(stopPlayback: suspend () -> Unit, forgetCast: () -> Unit) {
    try {
        stopPlayback()
    } finally {
        forgetCast()
    }
}
