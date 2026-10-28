package org.jellyfin.mobile.cast

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import org.jellyfin.mobile.cast.CastDevice.Repeat
import org.jellyfin.mobile.cast.DeviceStatus.IdleReason
import org.jellyfin.mobile.cast.DeviceStatus.State

/**
 * A cast device in a test: it takes each command a round trip later ([roundTripMs], on the main
 * looper and Robolectric's clock), says what it holds after each, and plays its queue as time goes
 * on, a song lasting its metadata's length less where its stream starts. The tests drive what a
 * real one does on its own: its remote ([pressPause], [skipOnRemote]), another sender ([takeOver]),
 * dropped commands ([failNext]) and dropped reports ([quiet]).
 */
internal class FakeCastDevice(
    private val roundTripMs: Long = ROUND_TRIP_MS,
    /**
     * Whether a command's answer comes before the listener hears what it did (its [status] has it
     * already, as the Cast framework's media status does when the answer comes).
     */
    private val answerFirst: Boolean = false,
) : CastDevice {

    private val handler = Handler(Looper.getMainLooper())

    override var isConnected = false
        private set
    override var status: DeviceStatus? = null
        private set
    override val deviceInfo: DeviceInfo =
        DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).setMaxVolume(CastDevice.MAX_VOLUME).build()
    override var volume = 10
        private set
    override var isMuted = false
        private set
    override var listener: CastDevice.Listener? = null

    /** What it holds: the items sent, in order, or another sender's (with no key). */
    val queue = mutableListOf<SentItem>()
    private var current = -1
    private var state = State.IDLE
    private var idleReason = IdleReason.NONE
    private var positionMs = 0L
    private var atMs = 0L
    var speed = 1f
        private set
    var repeat = Repeat.OFF
        private set
    private var other = false

    /** Every command taken, by name, in order. */
    val commands = mutableListOf<String>()

    /** How many of the next commands it fails. */
    var failNext = 0

    /** While true, it says nothing of what it does. */
    var quiet = false

    private val songEnds = Runnable(::songEnded)

    val keys: List<String> get() = queue.map { it.key }
    val playing: SentItem? get() = queue.getOrNull(current).takeUnless { other }
    val isPlaying get() = state == State.PLAYING
    val stateNow get() = state

    /** Where it is in its stream now. */
    val streamPositionMs: Long get() = positionAt(now())

    /** Where it is in the song now: its stream's place plus where the stream starts. */
    val songPositionMs: Long get() = streamPositionMs + (playing?.stream?.startMs ?: 0L)

    fun connect() {
        isConnected = true
        listener?.onConnectionChanged(true)
        report()
    }

    /** The session ends from the cast device's side (or the network goes). */
    fun drop() {
        isConnected = false
        status = null
        handler.removeCallbacksAndMessages(null)
        listener?.onConnectionChanged(false)
    }

    fun pressPause() = remote { pauseNow() }
    fun pressPlay() = remote { playNow() }
    fun skipOnRemote() = remote { if (current < queue.lastIndex) startAt(current + 1, 0L) }
    fun stopOnRemote() = remote { idle(IdleReason.CANCELED) }
    fun failToPlay() = remote { idle(IdleReason.ERROR) }

    /** Another app casts its own item to this device. */
    fun takeOver() = remote {
        queue.clear()
        queue += SentItem("", MediaItem.Builder().setMediaId("theirs").build(), CastStream("theirs", "audio/mpeg"))
        other = true
        startAt(0, 0L)
        state = State.PLAYING
    }

    private fun remote(change: () -> Unit) {
        change()
        report()
    }

    override fun load(
        items: List<SentItem>,
        startIndex: Int,
        startMs: Long,
        playWhenReady: Boolean,
        repeat: Repeat,
        speed: Float,
        done: (Boolean) -> Unit,
    ) = command("load ${items.map { it.key }} @$startMs", done) {
        queue.clear()
        queue += items
        other = false
        this.repeat = repeat
        this.speed = speed
        startAt(startIndex, startMs)
        if (playWhenReady) playNow() else pauseNow()
    }

    override fun append(items: List<SentItem>, done: (Boolean) -> Unit) =
        command("append ${items.map { it.key }}", done) { queue += items }

    override fun keepOnly(keys: Set<String>, done: (Boolean) -> Unit) = command("keepOnly $keys", done) {
        val playingKey = playing?.key
        queue.retainAll { it.key in keys }
        current = queue.indexOfFirst { it.key == playingKey }
        if (current < 0) idle(IdleReason.INTERRUPTED)
    }

    override fun jumpTo(key: String, startMs: Long, done: (Boolean) -> Unit) = command("jumpTo $key @$startMs", done) {
        val index = queue.indexOfFirst { it.key == key }
        check(index >= 0) { "jumped to $key, which isn't here" }
        val wasPlaying = state == State.PLAYING
        startAt(index, startMs)
        if (wasPlaying) playNow() else pauseNow()
    }

    override fun seek(positionMs: Long, done: (Boolean) -> Unit) = command("seek $positionMs", done) {
        val wasPlaying = state == State.PLAYING
        this.positionMs = positionMs
        atMs = now()
        if (wasPlaying) playNow()
    }

    override fun play(done: (Boolean) -> Unit) = command("play", done) { playNow() }
    override fun pause(done: (Boolean) -> Unit) = command("pause", done) { pauseNow() }

    override fun setSpeed(speed: Float, done: (Boolean) -> Unit) = command("speed $speed", done) {
        positionMs = streamPositionMs
        atMs = now()
        this.speed = speed
        if (state == State.PLAYING) playNow()
    }

    override fun setRepeat(repeat: Repeat, done: (Boolean) -> Unit) =
        command("repeat $repeat", done) { this.repeat = repeat }

    override fun stop(done: (Boolean) -> Unit) = command("stop", done) {
        queue.clear()
        idle(IdleReason.CANCELED)
        current = -1
    }

    override fun setVolume(volume: Int) {
        this.volume = volume
        listener?.onVolumeChanged()
    }

    override fun setMuted(muted: Boolean) {
        isMuted = muted
        listener?.onVolumeChanged()
    }

    var disconnected = false
        private set

    override fun disconnect() {
        disconnected = true
        drop()
    }

    var released = false
        private set

    override fun release() {
        released = true
        handler.removeCallbacksAndMessages(null)
    }

    private fun command(name: String, done: (Boolean) -> Unit, apply: () -> Unit) {
        check(isConnected) { "$name with no session" }
        commands += name
        handler.postDelayed(
            {
                if (failNext > 0) {
                    failNext--
                    done(false)
                } else {
                    apply()
                    if (answerFirst) {
                        val said = report(notify = false)
                        done(true)
                        said?.let { listener?.onStatus(it) }
                    } else {
                        report()
                        done(true)
                    }
                }
            },
            roundTripMs,
        )
    }

    private fun startAt(index: Int, startMs: Long) {
        current = index
        positionMs = startMs
        atMs = now()
        idleReason = IdleReason.NONE
        if (state == State.IDLE) state = State.PAUSED
    }

    private fun playNow() {
        if (current !in queue.indices) return
        positionMs = streamPositionMs
        atMs = now()
        state = State.PLAYING
        handler.removeCallbacks(songEnds)
        val left = streamLength(current) - positionMs
        if (left >= 0) handler.postDelayed(songEnds, (left / speed).toLong())
    }

    private fun pauseNow() {
        if (current !in queue.indices) return
        positionMs = streamPositionMs
        atMs = now()
        state = State.PAUSED
        handler.removeCallbacks(songEnds)
    }

    private fun idle(reason: IdleReason) {
        positionMs = streamPositionMs
        atMs = now()
        state = State.IDLE
        idleReason = reason
        handler.removeCallbacks(songEnds)
    }

    private fun songEnded() {
        when {
            repeat == Repeat.ONE -> startAt(current, 0L)
            current < queue.lastIndex -> startAt(current + 1, 0L)
            repeat == Repeat.ALL && queue.isNotEmpty() -> startAt(0, 0L)
            else -> {
                positionMs = streamLength(current)
                atMs = now()
                state = State.IDLE
                idleReason = IdleReason.FINISHED
                report()
                return
            }
        }
        playNow()
        report()
    }

    private fun streamLength(index: Int): Long {
        val sent = queue[index]
        val song = sent.item.mediaMetadata.durationMs ?: return Long.MAX_VALUE / 2
        return song - sent.stream.startMs
    }

    private fun positionAt(nowMs: Long): Long =
        if (state == State.PLAYING) positionMs + ((nowMs - atMs) * speed).toLong() else positionMs

    private fun now() = SystemClock.elapsedRealtime()

    private fun report(notify: Boolean = true): DeviceStatus? {
        if (quiet || !isConnected) return null
        val playingItem = queue.getOrNull(current)
        val next = DeviceStatus(
            keys = queue.map { it.key.takeUnless { other } },
            currentKey = playingItem?.key?.takeUnless { other || state == State.IDLE && current < 0 },
            playsOther = other,
            state = state,
            idleReason = idleReason,
            positionMs = streamPositionMs,
            atMs = now(),
            speed = speed,
            durationMs = if (playingItem?.stream?.seekable == true) streamLength(current) else C.TIME_UNSET,
            repeat = repeat,
            items = queue.map { it.item.takeUnless { other } },
        )
        status = next
        if (notify) listener?.onStatus(next)
        return next
    }

    companion object {
        const val ROUND_TRIP_MS = 100L
    }
}
