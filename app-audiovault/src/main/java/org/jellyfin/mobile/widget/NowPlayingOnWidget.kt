package org.jellyfin.mobile.widget

import android.content.Context
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jellyfin.mobile.player.playback.toAudiobookTrack

/**
 * Keeps the home-screen widget showing what the playback service's player plays: each player
 * [follow]ed reports while it's the one the session plays on (this device's, or a cast device's),
 * and only a change is drawn.
 */
class NowPlayingOnWidget(
    private val context: Context,
    private val scope: CoroutineScope,
    private val draw: suspend (Context, NowPlaying?) -> Unit = AudiobookWidget::show,
) {
    private var drawn: NowPlaying? = null
    private var drawnOnce = false

    fun follow(player: Player, isShowing: (Player) -> Boolean) {
        player.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (!isShowing(player)) return
                    val playing = player.playWhenReady && player.playbackState != Player.STATE_ENDED
                    show(nowPlayingOf(player.currentMediaItem?.toAudiobookTrack(), playing))
                }
            },
        )
    }

    fun show(now: NowPlaying?) {
        if (drawnOnce && now == drawn) return
        drawn = now
        drawnOnce = true
        scope.launch { draw(context, now) }
    }
}
