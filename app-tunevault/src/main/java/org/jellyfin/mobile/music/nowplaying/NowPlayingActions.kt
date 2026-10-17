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
    val onOpenAlbum: (albumId: String) -> Unit = {},
    val onOpenArtist: (artistId: String) -> Unit = {},
)

/** Test tags for [NowPlayingScreen]. */
object NowPlayingTags {
    const val COVER = "now_playing_cover"
}
