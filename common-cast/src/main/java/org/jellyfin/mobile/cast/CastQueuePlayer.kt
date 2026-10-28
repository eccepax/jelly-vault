package org.jellyfin.mobile.cast

import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.jellyfin.mobile.cast.DeviceStatus.IdleReason
import java.util.UUID
import kotlin.math.abs

/**
 * Playback on a cast device, with the queue kept here. The queue, which song is playing and where,
 * play or pause, repeat and speed are this player's own, so everything that reads it (the session,
 * the notification, an app's player screen) gets the app's items as they were given, and they're
 * still here once the cast ends. The cast device holds a window of the queue: the song playing and
 * the [window] − 1 after it, each sent with the key of its entry here, so the two are matched by
 * key, never by index or count.
 *
 * What the cast device should hold follows from the state here, and it's brought there one command
 * at a time: whenever it says what it holds or answers a command, [sync] compares the two and sends
 * the next step. The cast device moving on by itself (a song ending, its remote) is followed when no
 * change from here is on its way; meanwhile what it says of the song and the place is set aside.
 *
 * Each song's stream comes from its [CastSource], from where it should start: a transcode can't
 * seek, so it's asked for from the place and sent again for a seek, and places are always the
 * song's. [sources] finds each entry's source once, perhaps later (it may ask the server what the
 * file is); a song goes to the cast device once its source is found, and the songs after it as
 * theirs are, without holding it up.
 */
// One handler per Player command SimpleBasePlayer takes, and a few steps of keeping the cast device in line
@Suppress("TooManyFunctions")
@UnstableApi
internal class CastQueuePlayer(
    private val device: CastDevice,
    private val sources: CastSourceFinder,
    private val window: Int = WINDOW,
    /** How far skip back and forward go: as far as this device's player does (see SessionCasting). */
    private val seekBackMs: Long = C.DEFAULT_SEEK_BACK_INCREMENT_MS,
    private val seekForwardMs: Long = C.DEFAULT_SEEK_FORWARD_INCREMENT_MS,
    looper: Looper = Looper.getMainLooper(),
    private val now: () -> Long = SystemClock::elapsedRealtime,
) : SimpleBasePlayer(looper), CastDevice.Listener {

    private class Entry(val key: String, val item: MediaItem)

    /** A place in a song, moving at [speed] from [atMs] while [moving]. */
    private data class Anchor(val positionMs: Long, val atMs: Long, val moving: Boolean, val speed: Float) {
        fun at(nowMs: Long): Long = if (moving) positionMs + ((nowMs - atMs) * speed).toLong() else positionMs
    }

    /** Where playback should be on the cast device, and isn't known to be yet. */
    private class Target(val key: String, val positionMs: Long, val reload: Boolean = false)

    private val entries = mutableListOf<Entry>()
    private var index = 0
    private var anchor = Anchor(0L, now(), moving = false, speed = 1f)
    private var playWhenReady = false
    private var prepared = false
    private var ended = false
    private var error: PlaybackException? = null
    private var repeatMode = Player.REPEAT_MODE_OFF
    private var speed = 1f
    private var target: Target? = null

    /** The streams the cast device was sent, by key. */
    private val sent = mutableMapOf<String, CastStream>()

    /** Each entry's source, by key, once found; and the entries whose sources are being looked for. */
    private val found = mutableMapOf<String, CastSource>()
    private val looking = mutableSetOf<String>()
    private var lookingNow = false

    private var inFlight = false
    private var failures = 0

    /** Counts sessions, so an answer from an earlier one changes nothing. */
    private var connection = 0
    private var released = false

    /** What the cast device last said with nothing from here on its way. */
    private var lastKey: String? = null
    private var lastPlaying: Boolean? = null

    private var playlistVersion = 0
    private var playlist: Pair<Triple<Int, Int, Long>, List<MediaItemData>>? = null

    /**
     * The volume last asked for (a sleep timer's fade, say). Never the cast device's own (that's the
     * TV's or speaker's), so it's kept here, for this device's player once playback is back.
     */
    var volumeAskedFor: Float? = null
        private set

    /** Told when a cast session comes up or ends, once this has caught up with it (see SessionCasting). */
    var onConnectionChange: ((connected: Boolean) -> Unit)? = null

    /** Taking up what the cast device plays, once it has said (see [takeUpDevice]). */
    private var takingUp = false

    /** Taking up play or pause as the cast device next says it, from a queue taken up while it buffered. */
    private var adoptPlayback = false

    init {
        device.listener = this
    }

    private val current: Entry? get() = entries.getOrNull(index)

    private val position: Long get() = clampToSong(anchor.at(now()))

    private fun clampToSong(positionMs: Long): Long {
        val duration = durationOf(index)
        return if (duration != C.TIME_UNSET) positionMs.coerceAtMost(duration) else positionMs
    }

    /** How long the entry at [i] lasts: its metadata's length, else what the cast device says. */
    private fun durationOf(i: Int): Long {
        val entry = entries.getOrNull(i) ?: return C.TIME_UNSET
        return entry.item.mediaMetadata.durationMs ?: deviceDurationOf(entry)
    }

    private fun deviceDurationOf(entry: Entry): Long {
        val status = device.status?.takeIf { it.currentKey == entry.key } ?: return C.TIME_UNSET
        if (status.durationMs == C.TIME_UNSET) return C.TIME_UNSET
        return status.durationMs + (sent[entry.key]?.startMs ?: 0L)
    }

    override fun getState(): State {
        val playbackState = playbackState()
        return State.Builder()
            .setAvailableCommands(COMMANDS)
            .setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(playbackState)
            .setPlayerError(error)
            .setRepeatMode(repeatMode)
            .setPlaybackParameters(PlaybackParameters(speed))
            .setVolume(volumeAskedFor ?: 1f)
            .setDeviceInfo(device.deviceInfo)
            .setDeviceVolume(device.volume)
            .setIsDeviceMuted(device.isMuted)
            .setPlaylist(playlist())
            .setCurrentMediaItemIndex(if (entries.isEmpty()) C.INDEX_UNSET else index)
            .setContentPositionMs { position }
            .setIsLoading(inFlight && playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED)
            .setSeekBackIncrementMs(seekBackMs)
            .setSeekForwardIncrementMs(seekForwardMs)
            .setMaxSeekToPreviousPositionMs(C.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS)
            .build()
    }

    private fun playbackState(): @Player.State Int {
        val status = device.status
        return when {
            !prepared -> Player.STATE_IDLE
            entries.isEmpty() || ended -> Player.STATE_ENDED
            target != null || status == null || status.currentKey != current?.key -> Player.STATE_BUFFERING
            status.state == DeviceStatus.State.PLAYING || status.state == DeviceStatus.State.PAUSED -> Player.STATE_READY
            else -> Player.STATE_BUFFERING
        }
    }

    /** Made again only when the queue, the song playing or its length (which can come from the cast device) changes. */
    private fun playlist(): List<MediaItemData> {
        val key = Triple(playlistVersion, index, durationOf(index))
        playlist?.takeIf { it.first == key }?.let { return it.second }
        val items = entries.mapIndexed { i, entry ->
            val duration = durationOf(i)
            MediaItemData.Builder(entry.key)
                .setMediaItem(entry.item)
                .setDurationUs(if (duration == C.TIME_UNSET) C.TIME_UNSET else duration * US_PER_MS)
                .setIsSeekable(true)
                .build()
        }
        playlist = key to items
        return items
    }

    private fun queueChanged() {
        playlistVersion++
        val keys = entries.mapTo(mutableSetOf()) { it.key }
        found.keys.retainAll(keys)
    }

    // Playback

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        this.playWhenReady = playWhenReady
        if (!playWhenReady) anchor = anchor.copy(positionMs = position, atMs = now(), moving = false)
        return changed()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        prepared = true
        // After an error the cast device may still hold the song it couldn't play: it's sent again
        val playing = current
        if (error != null && playing != null) target = Target(playing.key, position, reload = true)
        error = null
        failures = 0
        return changed()
    }

    override fun handleStop(): ListenableFuture<*> {
        prepared = false
        target = null
        anchor = anchor.copy(positionMs = position, atMs = now(), moving = false)
        return changed()
    }

    override fun handleRelease(): ListenableFuture<*> {
        released = true
        device.listener = null
        device.release()
        return Futures.immediateVoidFuture()
    }

    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        this.repeatMode = repeatMode
        return changed()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        anchor = anchor.copy(positionMs = position, atMs = now(), speed = playbackParameters.speed)
        speed = playbackParameters.speed
        return changed()
    }

    override fun handleSetVolume(volume: Float): ListenableFuture<*> {
        volumeAskedFor = volume
        return Futures.immediateVoidFuture()
    }

    override fun handleSetDeviceVolume(deviceVolume: Int, flags: Int): ListenableFuture<*> {
        device.setVolume(deviceVolume.coerceIn(0, CastDevice.MAX_VOLUME))
        return Futures.immediateVoidFuture()
    }

    override fun handleIncreaseDeviceVolume(flags: Int): ListenableFuture<*> =
        handleSetDeviceVolume(device.volume + 1, flags)

    override fun handleDecreaseDeviceVolume(flags: Int): ListenableFuture<*> =
        handleSetDeviceVolume(device.volume - 1, flags)

    override fun handleSetDeviceMuted(muted: Boolean, flags: Int): ListenableFuture<*> {
        device.setMuted(muted)
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        if (entries.isEmpty()) return Futures.immediateVoidFuture()
        val start = if (positionMs == C.TIME_UNSET) 0L else positionMs.coerceAtLeast(0L)
        moveTo(mediaItemIndex.coerceIn(entries.indices), start)
        return changed()
    }

    /** Playback goes to the entry at [i], [positionMs] in, once the cast device gets there. */
    private fun moveTo(i: Int, positionMs: Long) {
        index = i
        ended = false
        anchor = Anchor(positionMs, now(), moving = false, speed = speed)
        target = Target(entries[i].key, positionMs)
    }

    // The queue

    override fun handleSetMediaItems(mediaItems: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
        entries.clear()
        entries += mediaItems.map(::newEntry)
        queueChanged()
        ended = false
        target = null
        if (entries.isEmpty()) {
            index = 0
            anchor = Anchor(0L, now(), moving = false, speed = speed)
        } else {
            val start = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(entries.indices)
            moveTo(start, if (startPositionMs == C.TIME_UNSET) 0L else startPositionMs)
        }
        return changed()
    }

    override fun handleAddMediaItems(index: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        val wasEmpty = entries.isEmpty()
        val at = index.coerceIn(0, entries.size)
        entries.addAll(at, mediaItems.map(::newEntry))
        queueChanged()
        when {
            wasEmpty && entries.isNotEmpty() -> moveTo(0, 0L)
            !wasEmpty && at <= this.index -> this.index += mediaItems.size
        }
        return changed()
    }

    override fun handleMoveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int): ListenableFuture<*> {
        val playing = current
        val moved = entries.subList(fromIndex, toIndex).toList()
        entries.subList(fromIndex, toIndex).clear()
        entries.addAll(newIndex.coerceIn(0, entries.size), moved)
        queueChanged()
        if (playing != null) index = entries.indexOf(playing)
        return changed()
    }

    override fun handleReplaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>): ListenableFuture<*> {
        val replaced = entries.subList(fromIndex, toIndex).toList()
        val playingReplaced = index in fromIndex until toIndex
        // The same song with new details stays the same entry, and keeps playing
        val replacements = mediaItems.mapIndexed { i, item ->
            replaced.getOrNull(i)?.takeIf { it.item.mediaId == item.mediaId }?.let { Entry(it.key, item) } ?: newEntry(item)
        }
        val playingKey = current?.key
        entries.subList(fromIndex, toIndex).clear()
        entries.addAll(fromIndex, replacements)
        queueChanged()
        val stillThere = entries.indexOfFirst { it.key == playingKey }
        when {
            stillThere >= 0 -> index = stillThere
            playingReplaced && entries.isNotEmpty() -> moveTo(fromIndex.coerceAtMost(entries.lastIndex), 0L)
        }
        return changed()
    }

    override fun handleRemoveMediaItems(fromIndex: Int, toIndex: Int): ListenableFuture<*> {
        val playingRemoved = index in fromIndex until toIndex
        entries.subList(fromIndex, toIndex).clear()
        queueChanged()
        when {
            entries.isEmpty() -> {
                index = 0
                target = null
            }
            playingRemoved && fromIndex < entries.size -> moveTo(fromIndex, 0L)
            playingRemoved -> {
                // Past the end, as this device's player does: ended, at the last song
                index = entries.lastIndex
                ended = true
                target = null
            }
            index >= toIndex -> index -= toIndex - fromIndex
        }
        return changed()
    }

    private fun newEntry(item: MediaItem) = Entry(UUID.randomUUID().toString(), item)

    /**
     * Takes up what the cast device is playing as the queue, from another of this app's players (the
     * service was started again while casting): there's nothing here to cast instead.
     */
    fun takeUpDevice() {
        val status = device.status
        if (status == null || !status.queueKnown) {
            takingUp = true
            return
        }
        takeUp(status)
    }

    private fun takeUp(status: DeviceStatus) {
        takingUp = false
        if (entries.isNotEmpty() || status.playsOther || status.currentKey == null) return
        val held = device.heldItems().filterNotNull()
        entries.clear()
        entries += held.map { Entry(it.key, it.item) }
        queueChanged()
        index = entries.indexOfFirst { it.key == status.currentKey }.coerceAtLeast(0)
        // Where each one's stream starts, so places stay the song's
        sent.clear()
        held.forEach { sent[it.key] = it.stream }
        prepared = true
        playWhenReady = status.isPlaying
        // Buffering says neither play nor pause: the first that it does say is taken up as it is
        adoptPlayback = status.playsOrPauses == null
        repeatMode = when (status.repeat) {
            CastDevice.Repeat.OFF -> Player.REPEAT_MODE_OFF
            CastDevice.Repeat.ONE -> Player.REPEAT_MODE_ONE
            CastDevice.Repeat.ALL -> Player.REPEAT_MODE_ALL
        }
        speed = status.speed
        target = null
        followDevice(status)
        invalidateState()
    }

    // The cast device

    override fun onConnectionChanged(connected: Boolean) {
        if (released) return
        connection++
        inFlight = false
        failures = 0
        lastKey = null
        lastPlaying = null
        if (!connected) {
            // Where it had got to, as far as anyone knows, and no further
            leaveAt(now())
            target = null
            takingUp = false
        }
        sync()
        invalidateState()
        onConnectionChange?.invoke(connected)
    }

    override fun onStatus(status: DeviceStatus) {
        if (released) return
        if (takingUp && status.queueKnown) takeUp(status)
        followDevice(status)
        sync()
        invalidateState()
    }

    override fun onVolumeChanged() {
        if (!released) invalidateState()
    }

    /** Whether what the cast device says now is its own doing: nothing from here is on its way. */
    private val settled: Boolean get() = target == null && !inFlight

    /** What the cast device has done by itself (with nothing from here on its way), this does too. */
    private fun followDevice(status: DeviceStatus) {
        if (entries.isEmpty() || !settled || !status.queueKnown) return
        if (status.playsOther) {
            // Another app took the cast device over: playback here stops, the queue stays
            if (prepared) stopHere()
            return
        }
        val key = status.currentKey
        if (key != null && key != lastKey) followMove(key)
        if (key != null && key == current?.key && status.state != DeviceStatus.State.IDLE) followPlayback(key, status)
        if (status.state == DeviceStatus.State.IDLE && prepared) followIdle(status)
        lastKey = key
        status.playsOrPauses?.let { lastPlaying = it }
    }

    /** It moved on to [key] (a song ended, its remote): so does this. */
    private fun followMove(key: String) {
        val moved = entries.indexOfFirst { it.key == key }
        if (moved >= 0 && moved != index) {
            index = moved
            ended = false
        }
    }

    /** Its place in the song playing, and play or pause where its remote changed that. */
    private fun followPlayback(key: String, status: DeviceStatus) {
        val playsOrPauses = status.playsOrPauses
        if (adoptPlayback && playsOrPauses != null) {
            playWhenReady = playsOrPauses
            adoptPlayback = false
        }
        if (lastPlaying != null && playsOrPauses != null && playsOrPauses != lastPlaying) playWhenReady = playsOrPauses
        anchor = Anchor(
            positionMs = status.positionMs + (sent[key]?.startMs ?: 0L),
            atMs = status.atMs,
            moving = status.state == DeviceStatus.State.PLAYING,
            speed = status.speed,
        )
    }

    private fun stopHere() {
        prepared = false
        playWhenReady = false
        anchor = anchor.copy(positionMs = position, atMs = now(), moving = false)
    }

    private fun followIdle(status: DeviceStatus) {
        val key = status.currentKey ?: lastKey
        if (key != current?.key) return
        when (status.idleReason) {
            IdleReason.FINISHED -> {
                val next = nextIndex()
                if (next != C.INDEX_UNSET) {
                    moveTo(next, 0L)
                } else if (!ended) {
                    ended = true
                    anchor = Anchor(durationOf(index).takeIf { it != C.TIME_UNSET } ?: position, now(), false, speed)
                }
            }
            IdleReason.ERROR -> fail()
            // Stopped there (its remote, the TV's own controls): stopped here too
            IdleReason.CANCELED -> stopHere()
            IdleReason.NONE, IdleReason.INTERRUPTED -> Unit
        }
    }

    private fun nextIndex(): Int = when {
        repeatMode == Player.REPEAT_MODE_ONE -> index
        index < entries.lastIndex -> index + 1
        repeatMode == Player.REPEAT_MODE_ALL -> 0
        else -> C.INDEX_UNSET
    }

    /**
     * Stops where playback had got to by [nowMs]: on at its speed while it was playing, through the
     * songs whose lengths are known and as repeat would take it; at the end after the last.
     */
    private fun leaveAt(nowMs: Long) {
        var position = anchor.at(nowMs)
        var duration = durationOf(index)
        while (duration != C.TIME_UNSET && position >= duration) {
            val next = nextIndex()
            if (next == C.INDEX_UNSET) {
                position = duration
                ended = true
                break
            }
            position -= duration
            index = next
            duration = durationOf(index)
        }
        anchor = Anchor(position, nowMs, moving = false, speed = speed)
    }

    private fun fail() {
        error = PlaybackException(
            "The cast device couldn't play this",
            null,
            PlaybackException.ERROR_CODE_REMOTE_ERROR,
        )
        prepared = false
        target = null
        anchor = anchor.copy(positionMs = position, atMs = now(), moving = false)
    }

    // Bringing the cast device to the state here

    private fun changed(): ListenableFuture<*> {
        sync()
        return Futures.immediateVoidFuture()
    }

    /** Sends the next step towards what the cast device should hold, unless one is on its way. */
    private fun sync() {
        if (released || inFlight || !device.isConnected) return
        // Until it has said what all its items are, which it's playing isn't known: it says again once it has
        val status = device.status?.takeIf { it.queueKnown } ?: return
        val playing = current
        if (!prepared || playing == null) {
            if (status.state != DeviceStatus.State.IDLE && !status.playsOther) {
                send(
                    touchesPlayback = true,
                ) { device.stop(it) }
            }
            return
        }
        if (ended || error != null || status.playsOther) return
        val wanted = windowFrom(index)
        val goTo = target
        when {
            goTo != null -> go(goTo, status, wanted)
            status.currentKey != playing.key -> load(wanted, position, goTo)
            // Songs after it still to be found don't hold up play, pause and the rest
            status.keys != wanted.map { it.key } && fixTail(status, wanted) -> Unit
            else -> syncPlayback(status, deviceRepeat(status.keys.size == entries.size))
        }
    }

    /** It holds the right songs and plays the right one: repeat, play or pause, and speed. */
    private fun syncPlayback(status: DeviceStatus, repeat: CastDevice.Repeat) {
        when {
            status.repeat != repeat -> send { device.setRepeat(repeat, it) }
            // Buffering says neither: a paused cast device buffers too, and isn't told to pause again
            status.playsOrPauses.let { it != null && it != playWhenReady } ->
                send(touchesPlayback = true) { if (playWhenReady) device.play(it) else device.pause(it) }
            abs(status.speed - speed) > SPEED_TOLERANCE -> send { device.setSpeed(speed, it) }
        }
    }

    /**
     * The cast device repeats by itself only when each stream it holds starts at its song's start:
     * one started part way (a transcode) would repeat from there, so this goes round instead.
     */
    private fun deviceRepeat(wholeQueueSent: Boolean): CastDevice.Repeat =
        if (sent.values.any { it.startMs != 0L }) CastDevice.Repeat.OFF else repeatFor(repeatMode, wholeQueueSent)

    /** The entries the cast device should hold: [window] of them from [from], round the queue as repeat goes. */
    private fun windowFrom(from: Int): List<Entry> {
        if (repeatMode == Player.REPEAT_MODE_ONE) return listOf(entries[from])
        val wanted = mutableListOf(entries[from])
        var i = from
        while (wanted.size < window) {
            i = when {
                i < entries.lastIndex -> i + 1
                repeatMode == Player.REPEAT_MODE_ALL -> 0
                else -> break
            }
            if (i == from) break
            wanted += entries[i]
        }
        return wanted
    }

    private fun go(goTo: Target, status: DeviceStatus, wanted: List<Entry>) {
        val stream = sent[goTo.key]
        val confirm = { ok: Boolean -> if (ok && target === goTo) target = null }
        when {
            goTo.reload || stream == null || !stream.seekable && goTo.positionMs != stream.startMs ->
                load(wanted, goTo.positionMs, goTo)
            status.currentKey == goTo.key -> send(
                confirm,
                touchesPlayback = true,
            ) { device.seek(goTo.positionMs - stream.startMs, it) }
            goTo.key in status.keys ->
                send(confirm, touchesPlayback = true) { device.jumpTo(goTo.key, goTo.positionMs - stream.startMs, it) }
            else -> load(wanted, goTo.positionMs, goTo)
        }
    }

    /** Sends [wanted] from the first, [fromMs] in: as many as have their sources, once the first has. */
    private fun load(wanted: List<Entry>, fromMs: Long, goTo: Target?) {
        val ready = withSources(wanted)
        if (ready.isEmpty()) return
        val items = ready.mapIndexed { i, entry -> sentItem(entry, if (i == 0) fromMs else 0L) }
        sent.clear()
        items.forEach { sent[it.key] = it.stream }
        val repeat = deviceRepeat(ready.size == entries.size)
        send({ ok -> if (ok && target === goTo) target = null }, touchesPlayback = true) {
            device.load(items, 0, fromMs - items[0].stream.startMs, playWhenReady, repeat, speed, it)
        }
    }

    /**
     * The cast device plays the right song: the songs after it are brought in line by key. False if
     * there's nothing to send yet (the next songs' sources are still being found).
     */
    private fun fixTail(status: DeviceStatus, wanted: List<Entry>): Boolean {
        val wantedKeys = wanted.map { it.key }
        val held = status.keys
        when {
            held.any { it == null || it !in wantedKeys } -> send { device.keepOnly(wantedKeys.toSet(), it) }
            held == wantedKeys.take(held.size) -> {
                val more = withSources(wanted.drop(held.size)).map { sentItem(it, 0L) }
                if (more.isEmpty()) return false
                more.forEach { sent[it.key] = it.stream }
                send { device.append(more, it) }
            }
            // In another order: all but the song playing go, and come back in order
            else -> send { device.keepOnly(setOf(wantedKeys.first()), it) }
        }
        return true
    }

    private fun sentItem(entry: Entry, fromMs: Long) =
        SentItem(entry.key, entry.item, found.getValue(entry.key).streamFrom(fromMs))

    /**
     * The first of [wanted] that have their sources, up to one that hasn't; those without are looked
     * for, and once one is found what the cast device should hold is looked at again.
     */
    private fun withSources(wanted: List<Entry>): List<Entry> {
        lookingNow = true
        wanted.filter { it.key !in found && it.key !in looking }.forEach(::lookFor)
        lookingNow = false
        return wanted.takeWhile { it.key in found }
    }

    private fun lookFor(entry: Entry) {
        looking += entry.key
        sources.find(entry.item) { source ->
            looking -= entry.key
            if (released || entries.none { it.key == entry.key }) return@find
            found[entry.key] = source
            // Found at once, it's sent with the rest; found later, it's sent now
            if (!lookingNow) {
                sync()
                invalidateState()
            }
        }
    }

    /**
     * Sends a command, and when it's answered looks at what's next. One that [touchesPlayback] (may
     * change play or pause) leaves what the cast device says of that as its doing, not its remote's.
     */
    private fun send(
        onDone: (Boolean) -> Unit = {},
        touchesPlayback: Boolean = false,
        command: ((Boolean) -> Unit) -> Unit,
    ) {
        inFlight = true
        val session = connection
        command { ok ->
            if (released || session != connection) return@command
            inFlight = false
            // Lost with the session gone for a moment, not turned down: it goes again once the session's back
            if (!ok && !device.isConnected) return@command
            if (touchesPlayback) lastPlaying = null
            onDone(ok)
            if (ok) failures = 0 else failed()
            device.status?.let(::followDevice)
            sync()
            invalidateState()
        }
    }

    /** The cast device turned a command down: the queue goes again from where it is, then it's an error. */
    private fun failed() {
        failures++
        val playing = current ?: return
        if (failures >= MAX_FAILURES) {
            fail()
        } else {
            target = Target(playing.key, position, reload = true)
        }
    }

    companion object {
        /** The song playing and the two after it: enough to go on to the next song without a gap. */
        const val WINDOW = 3
        private const val MAX_FAILURES = 2
        private const val SPEED_TOLERANCE = 0.01f
        private const val US_PER_MS = 1_000L

        private val COMMANDS = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT,
                Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SEEK_BACK,
                Player.COMMAND_SEEK_FORWARD,
                Player.COMMAND_SET_SPEED_AND_PITCH,
                Player.COMMAND_SET_REPEAT_MODE,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_SET_MEDIA_ITEM,
                Player.COMMAND_CHANGE_MEDIA_ITEMS,
                Player.COMMAND_GET_VOLUME,
                Player.COMMAND_SET_VOLUME,
                Player.COMMAND_GET_DEVICE_VOLUME,
                Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS,
                Player.COMMAND_RELEASE,
            )
            .build()
    }
}

/** Finds a song's [CastSource] for [CastQueuePlayer], at once or later, answering on the main thread. */
internal fun interface CastSourceFinder {
    fun find(item: MediaItem, found: (CastSource) -> Unit)
}
