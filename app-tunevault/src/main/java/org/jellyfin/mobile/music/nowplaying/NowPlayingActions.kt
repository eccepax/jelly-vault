package org.jellyfin.mobile.music.nowplaying

/** Everything the Now Playing screen can ask for. */
data class NowPlayingActions(
    val onCollapse: () -> Unit = {},
    val onTogglePlayPause: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onSeek: (positionMs: Long) -> Unit = {},
    val onCycleRepeat: () -> Unit = {},
    val onToggleShuffle: () -> Unit = {},
    val onOpenQueue: () -> Unit = {},
    val onToggleFavorite: () -> Unit = {},
)
