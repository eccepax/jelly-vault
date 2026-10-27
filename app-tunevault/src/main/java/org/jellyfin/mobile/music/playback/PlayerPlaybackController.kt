package org.jellyfin.mobile.music.playback

import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.mobile.music.domain.PlayQueue
import org.jellyfin.mobile.music.domain.RepeatMode
import org.jellyfin.mobile.music.domain.Track
import kotlin.random.Random

/**
 * [PlaybackController] over any Media3 [Player] (a MediaController in the app, a TestExoPlayer in
 * tests). Our [PlayQueue] is the source of truth for order: the player's playlist always mirrors
 * `queue.entries` in play order (so the player itself does gapless playback, auto-advance and
 * system-UI skips), and every player event re-syncs the queue's current entry from the player.
 * Must be used on the player's application thread.
 */
class PlayerPlaybackController(
    private val player: Player,
    private val streamUrl: (trackId: String) -> String,
    private val random: Random = Random.Default,
) : PlaybackController {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    override val positionMs: Long get() = player.currentPosition

    override val durationMs: Long get() = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L

    private val queue: PlayQueue get() = _state.value.queue

    init {
        player.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) = syncFromPlayer()
            },
        )
    }

    override fun play(tracks: List<Track>, startIndex: Int) {
        load(PlayQueue.of(tracks, startIndex).withRepeatMode(queue.repeatMode))
    }

    override fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val start = random.nextInt(tracks.size)
        load(PlayQueue.of(tracks, start).withRepeatMode(queue.repeatMode).shuffled(random))
    }

    override fun pause() = player.pause()

    override val volume: Float get() = player.volume

    override fun setVolume(volume: Float) {
        player.volume = volume
    }

    override fun togglePlayPause() {
        if (player.playWhenReady) {
            player.pause()
            return
        }
        when (player.playbackState) {
            Player.STATE_ENDED -> player.seekTo(playerIndexOf(queue), 0L)
            Player.STATE_IDLE -> player.prepare()
        }
        player.play()
    }

    override fun skipToNext() = seekToEntryOf(queue.skipToNext())

    override fun skipToPrevious() {
        val target = queue.skipToPrevious()
        if (positionMs > PlaybackController.RESTART_THRESHOLD_MS || target.currentIndex == queue.currentIndex) {
            player.seekTo(0L)
        } else {
            seekToEntryOf(target)
        }
    }

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

    override fun cycleRepeatMode() {
        val mode = queue.repeatMode.next()
        setQueue(queue.withRepeatMode(mode))
        player.repeatMode = mode.toPlayerRepeatMode()
    }

    override fun toggleShuffle() {
        if (queue.tracks.isEmpty()) return
        apply(if (queue.isShuffled) queue.unshuffled() else queue.shuffled(random))
    }

    override fun playNext(tracks: List<Track>) = apply(queue.playNext(tracks))

    override fun addToQueue(tracks: List<Track>) = apply(queue.addToQueue(tracks))

    override fun removeFromQueue(index: Int) = apply(queue.remove(index))

    override fun moveInQueue(from: Int, to: Int) = apply(queue.move(from, to))

    override fun skipTo(index: Int) = seekToEntryOf(queue.skipTo(index))

    override fun stop() {
        _state.update { PlaybackState(queue = PlayQueue.EMPTY.withRepeatMode(it.queue.repeatMode)) }
        player.stop()
        player.clearMediaItems()
    }

    /** Replaces the whole playlist, starting [newQueue]'s current entry from the top. */
    private fun load(newQueue: PlayQueue, autoPlay: Boolean = true) {
        setQueue(newQueue)
        player.repeatMode = newQueue.repeatMode.toPlayerRepeatMode()
        player.setMediaItems(newQueue.entries.map { it.toMediaItem(streamUrl) }, newQueue.currentIndex, 0L)
        player.prepare()
        if (autoPlay) player.play()
    }

    /**
     * Makes the player's playlist match [newQueue]. If the playing entry is still current, everything
     * around it is swapped out so it keeps playing uninterrupted (e.g. toggling shuffle mid-track).
     */
    private fun apply(newQueue: PlayQueue) {
        // When the playing entry itself goes (or nothing was loaded), reload: keep playing if we
        // were, and start if the queue was empty (e.g. "Add to queue" with nothing playing).
        val autoPlay = player.playWhenReady || player.mediaItemCount == 0
        val current = newQueue.entries.getOrNull(newQueue.currentIndex) ?: return load(newQueue, autoPlay)
        if (player.currentMediaItem?.queueUid != current.uid) return load(newQueue, autoPlay)

        setQueue(newQueue)
        val playingIndex = player.currentMediaItemIndex
        player.removeMediaItems(playingIndex + 1, player.mediaItemCount)
        player.removeMediaItems(0, playingIndex)
        val items = newQueue.entries.map { it.toMediaItem(streamUrl) }
        player.addMediaItems(0, items.subList(0, newQueue.currentIndex))
        player.addMediaItems(items.subList(newQueue.currentIndex + 1, items.size))
    }

    private fun seekToEntryOf(target: PlayQueue) {
        if (target.currentIndex != queue.currentIndex) player.seekTo(playerIndexOf(target), 0L)
    }

    /**
     * Where [target]'s current entry is in the player's playlist: its index in the queue, unless the
     * playlist lacks a song (back from a cast device, one it couldn't say how to play is left out).
     */
    private fun playerIndexOf(target: PlayQueue): Int {
        val uid = target.entries.getOrNull(target.currentIndex)?.uid
        if (player.getMediaItemAtOrNull(target.currentIndex)?.queueUid == uid) return target.currentIndex
        return (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).queueUid == uid }
            ?: target.currentIndex
    }

    private fun Player.getMediaItemAtOrNull(index: Int) = if (index in 0 until mediaItemCount) getMediaItemAt(
        index,
    ) else null

    private fun syncFromPlayer() {
        val playingUid = player.currentMediaItem?.queueUid
        _state.update { state ->
            val index = state.queue.entries.indexOfFirst { it.uid == playingUid }
            state.copy(
                queue = state.queue
                    .let { if (index >= 0) it.skipTo(index) else it }
                    .withRepeatMode(player.repeatMode.toRepeatMode()),
                isPlaying = player.isPlaying,
                isRemote = player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE,
            )
        }
    }

    private fun setQueue(newQueue: PlayQueue) = _state.update { it.copy(queue = newQueue) }
}

private fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
}

private fun Int.toRepeatMode(): RepeatMode = when (this) {
    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
    else -> RepeatMode.OFF
}
