package org.jellyfin.mobile.music.playback

import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.mobile.cast.CAST_QUEUE_LIMIT
import org.jellyfin.mobile.cast.castWindowFrom
import org.jellyfin.mobile.cast.sentFromAnotherApp
import org.jellyfin.mobile.music.domain.PlayQueue
import org.jellyfin.mobile.music.domain.QueueEntry
import org.jellyfin.mobile.music.domain.RepeatMode
import org.jellyfin.mobile.music.domain.Track
import kotlin.random.Random

/**
 * [PlaybackController] over any Media3 [Player] (a MediaController in the app, a TestExoPlayer in
 * tests). Our [PlayQueue] is the source of truth for order: the player's playlist always mirrors
 * `queue.entries` in play order (so the player itself does gapless playback, auto-advance and
 * system-UI skips), and every player event re-syncs the queue's current entry from the player.
 * While playback is on a cast device, which takes only so much in one message, a long queue is held
 * by the player [castWindow] songs at a time from the playing one (see [held]), moved on as it
 * plays; the queue itself stays whole. Entries are found in the player by uid, never by index.
 * Must be used on the player's application thread.
 */
class PlayerPlaybackController(
    private val player: Player,
    private val streamUrl: (trackId: String) -> String,
    private val random: Random = Random.Default,
    private val castWindow: Int = CAST_QUEUE_LIMIT,
) : PlaybackController {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    override val positionMs: Long get() = player.currentPosition

    override val durationMs: Long get() = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L

    private val queue: PlayQueue get() = _state.value.queue

    /**
     * The uids of the window last given the player while cast (see [keepHeld]); a cast device still
     * taking in its queue says it holds fewer, so this, not the player, says which song ends it.
     */
    private var sentWindow: List<Long> = emptyList()

    /**
     * Whether [sentWindow] was given the player while cast. Not when playback moved to the cast
     * device with the whole queue held here: Media3's transfer cut it down as it went.
     */
    private var sentWhileCast = false

    init {
        player.addListener(
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    syncFromPlayer()
                    keepHeld()
                }
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
            Player.STATE_ENDED -> player.seekTo(queue.current?.let { indexInPlayer(queue) } ?: 0, 0L)
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
        // A window held while cast wraps round to the start with repeat all, and doesn't without
        if (castWindowed(queue)) reshape(queue)
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
        val (entries, at) = held(newQueue)
        sentWindow = entries.map { it.uid }
        sentWhileCast = castWindowed(newQueue)
        player.setMediaItems(entries.map { it.toMediaItem(streamUrl) }, at, 0L)
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
        reshape(newQueue)
    }

    /**
     * The entries the player should hold for [queue], and where the current one is among them: all
     * of them, unless it's cast and longer than [castWindow]; then the current one and those after it
     * (round to the start again with repeat all), [castWindow] in all.
     */
    private fun held(queue: PlayQueue): Pair<List<QueueEntry>, Int> {
        val entries = queue.entries
        if (!castWindowed(queue)) return entries to queue.currentIndex
        val wraps = queue.repeatMode == RepeatMode.ALL
        val window = (queue.currentIndex until queue.currentIndex + castWindow)
            .map { if (wraps) it % entries.size else it }
            .takeWhile { it < entries.size }
        return window.map(entries::get) to 0
    }

    /** Whether the player holds [queue] a window at a time: cast, and longer than [castWindow]. */
    private fun castWindowed(queue: PlayQueue): Boolean = cast && queue.entries.size > castWindow

    private val cast: Boolean get() = player.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE

    /**
     * After each player event, once the player plays the queue's current entry: it holds what it
     * should. A window held while cast is left as it is while songs play through it, since Media3's
     * cast player can't take a change to its queue while a skip is on its way to the cast device
     * (it looks the skip's song up in a queue that no longer has it, and throws). Once its last song
     * plays with more to come, the next window is loaded from there in one go. One more than a window
     * (a queue moved to the cast device whole, say) is cut down to it; one cut to a window as it moved
     * there is taken as sent. A queue that fits is left as it was sent while cast. Played here, the
     * player holds the whole queue: put back only when it doesn't (back from a cast device with a
     * window), not rebuilt on every event.
     */
    private fun keepHeld() {
        val current = queue.entries.getOrNull(queue.currentIndex) ?: return
        if (player.currentMediaItem?.queueUid != current.uid) return
        if (!castWindowed(queue)) {
            // Cast, a queue that fits went there whole, and only edits from here change it. What the
            // cast device says it holds isn't put right: its status names songs wrongly for a moment
            // (as only an id, or the next song in the place of one ending), and sending the queue
            // again over that only fought it, over and over, until the queue there was gone
            if (!cast && !holdsWhole(queue)) reshape(queue)
            return
        }
        if (player.mediaItemCount > castWindow) return reshape(queue)
        if (!sentWhileCast) {
            sentWindow = queue.entries.castWindowFrom(queue.currentIndex, castWindow).map { it.uid }
            sentWhileCast = true
        }
        val (entries, _) = held(queue)
        if (sentWindow.lastOrNull() == current.uid && entries.size > 1) {
            sentWindow = entries.map { it.uid }
            sentWhileCast = true
            player.setMediaItems(entries.map { it.toMediaItem(streamUrl) }, 0, player.currentPosition)
        }
    }

    /**
     * Brings what the player holds around the playing entry (the queue's current one) to [held],
     * leaving it playing. Songs after it are only removed and added from where the two first differ,
     * so moving on a song touches one at each end.
     */
    private fun reshape(queue: PlayQueue) {
        val (entries, at) = held(queue)
        sentWindow = entries.map { it.uid }
        sentWhileCast = castWindowed(queue)
        val holding = List(player.mediaItemCount) { player.getMediaItemAt(it).queueUid }
        val playingIndex = player.currentMediaItemIndex
        val after = entries.subList(at + 1, entries.size)
        val holdingAfter = holding.subList(playingIndex + 1, holding.size)
        val same = after.zip(holdingAfter).takeWhile { (entry, uid) -> entry.uid == uid }.size
        if (same < holdingAfter.size) player.removeMediaItems(playingIndex + 1 + same, holding.size)
        if (same < after.size) player.addMediaItems(after.subList(same, after.size).map { it.toMediaItem(streamUrl) })
        val before = entries.subList(0, at)
        if (before.map { it.uid } != holding.subList(0, playingIndex)) {
            player.removeMediaItems(0, playingIndex)
            player.addMediaItems(0, before.map { it.toMediaItem(streamUrl) })
        }
    }

    /** Whether the player holds all of [queue], by its length and the songs at each end. */
    private fun holdsWhole(queue: PlayQueue): Boolean {
        val count = player.mediaItemCount
        return count == queue.entries.size &&
            player.getMediaItemAt(0).queueUid == queue.entries.first().uid &&
            player.getMediaItemAt(count - 1).queueUid == queue.entries.last().uid
    }

    /** Where the player holds [target]'s current entry; null if it doesn't. */
    private fun indexInPlayer(target: PlayQueue): Int? {
        val uid = target.entries.getOrNull(target.currentIndex)?.uid ?: return null
        return (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).queueUid == uid }
    }

    /** Plays [target]'s current entry from the start: where the player holds it, else loaded afresh. */
    private fun seekToEntryOf(target: PlayQueue) {
        if (target.currentIndex == queue.currentIndex) return
        val index = indexInPlayer(target)
        if (index != null) player.seekTo(index, 0L) else load(target, autoPlay = player.playWhenReady)
    }

    private fun syncFromPlayer() {
        // Every app on the phone casts in the one session: another app's book or song playing there
        // isn't this one's, so nothing plays here, and the queue's gone (play mustn't resume theirs)
        if (player.currentMediaItem?.sentFromAnotherApp == true) {
            _state.update { PlaybackState(queue = PlayQueue.EMPTY.withRepeatMode(it.queue.repeatMode)) }
            return
        }
        val playingUid = player.currentMediaItem?.queueUid
        _state.update { state ->
            val index = state.queue.entries.indexOfFirst { it.uid == playingUid }
            state.copy(
                queue = state.queue
                    .let { if (index >= 0) it.skipTo(index) else it }
                    .withRepeatMode(player.repeatMode.toRepeatMode()),
                isPlaying = player.isPlaying,
                isBuffering = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING,
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
