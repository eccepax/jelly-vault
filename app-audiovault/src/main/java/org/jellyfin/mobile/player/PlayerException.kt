package org.jellyfin.mobile.player

sealed class PlayerException(cause: Throwable?) : Exception(cause) {
    class InvalidPlayOptions(cause: Throwable? = null) : PlayerException(cause)
    class NetworkFailure(cause: Throwable? = null) : PlayerException(cause)
    class UnsupportedContent(cause: Throwable? = null) : PlayerException(cause)

    /** The playback service didn't come to play the book in time. */
    class StartTimedOut : PlayerException(null)
}
