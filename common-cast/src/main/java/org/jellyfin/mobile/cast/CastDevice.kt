package org.jellyfin.mobile.cast

import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

/**
 * What [CastQueuePlayer] needs of a cast device: the few commands the Default Media Receiver takes,
 * and what it says back. Items are named by the key [CastQueuePlayer] gave each entry of its queue,
 * which goes to the cast device with the item (see [RemoteMediaClientDevice]), so they're never
 * matched by index or count. Each command answers once through `done`, true if the cast device took
 * it. Main thread only, like the Cast framework.
 */
// One function per command the Default Media Receiver takes
@Suppress("TooManyFunctions")
internal interface CastDevice {

    /** Whether a cast session is up: commands go nowhere without one. */
    val isConnected: Boolean

    /** What the cast device said last in this session; null before it has said anything. */
    val status: DeviceStatus?

    /** Where the system routes the cast (so the volume keys reach it), and its volume out of [MAX_VOLUME]. */
    val deviceInfo: DeviceInfo
    val volume: Int
    val isMuted: Boolean

    var listener: Listener?

    /**
     * What it holds as it was sent, in the order of [DeviceStatus.keys]: the app's items and their
     * streams, for picking up a queue from another of this app's players; null for one this app
     * didn't send or the cast device hasn't said the details of. Read only when it's needed: it
     * goes through every item.
     */
    fun heldItems(): List<SentItem?>

    interface Listener {
        /**
         * A session came up or ended. A session can also come back after a moment without one (the
         * network went for a while): connected again, with the commands on their way then lost.
         */
        fun onConnectionChanged(connected: Boolean)

        fun onStatus(status: DeviceStatus)

        fun onVolumeChanged()
    }

    /** Replaces the cast device's queue with [items], playing [startIndex] from [startMs] into its stream. */
    fun load(
        items: List<SentItem>,
        startIndex: Int,
        startMs: Long,
        playWhenReady: Boolean,
        repeat: Repeat,
        speed: Float,
        done: (Boolean) -> Unit,
    )

    /** Adds [items] to the end of the cast device's queue. */
    fun append(items: List<SentItem>, done: (Boolean) -> Unit)

    /** Takes every item off the cast device's queue but those with these keys. */
    fun keepOnly(keys: Set<String>, done: (Boolean) -> Unit)

    /** Moves to the item with [key], [startMs] into its stream. */
    fun jumpTo(key: String, startMs: Long, done: (Boolean) -> Unit)

    /** Moves to [positionMs] in the item playing's stream. */
    fun seek(positionMs: Long, done: (Boolean) -> Unit)

    fun play(done: (Boolean) -> Unit)
    fun pause(done: (Boolean) -> Unit)
    fun setSpeed(speed: Float, done: (Boolean) -> Unit)
    fun setRepeat(repeat: Repeat, done: (Boolean) -> Unit)

    /** Stops the cast device, which then holds no queue. */
    fun stop(done: (Boolean) -> Unit)

    fun setVolume(volume: Int)
    fun setMuted(muted: Boolean)

    /** Ends the cast session. */
    fun disconnect()

    fun release()

    enum class Repeat { OFF, ONE, ALL }

    companion object {
        const val MAX_VOLUME = 20
    }
}

/** An entry of the queue as it goes to a cast device: its key, the app's item and the stream for it. */
internal data class SentItem(val key: String, val item: MediaItem, val stream: CastStream)

/**
 * What a cast device says of its queue and playback. [keys] are those of its items in order, null
 * for one this app didn't send; [currentKey] the item playing's, null when it plays none or another
 * app's ([playsOther]). [positionMs] is into the item's stream, at [atMs] (elapsed realtime).
 */
internal data class DeviceStatus(
    val keys: List<String?>,
    val currentKey: String?,
    val playsOther: Boolean,
    val state: State,
    val idleReason: IdleReason,
    val positionMs: Long,
    val atMs: Long,
    val speed: Float,
    /** How long the item's stream lasts; C.TIME_UNSET when it doesn't say (a transcode). */
    val durationMs: Long,
    val repeat: CastDevice.Repeat = CastDevice.Repeat.OFF,
    /** False while the cast device hasn't yet said what some of its items are: [keys] isn't the whole story. */
    val queueKnown: Boolean = true,
) {
    enum class State { IDLE, BUFFERING, PLAYING, PAUSED }

    enum class IdleReason { NONE, FINISHED, CANCELED, INTERRUPTED, ERROR }

    val isPlaying: Boolean get() = state == State.PLAYING || state == State.BUFFERING

    /**
     * Whether it's been told to play (true) or pause (false); null while that isn't known. Buffering
     * says neither: a paused cast device buffers too, after a seek from its remote.
     */
    val playsOrPauses: Boolean?
        get() = when (state) {
            State.PLAYING -> true
            State.PAUSED -> false
            State.BUFFERING, State.IDLE -> null
        }
}

/** The Default Media Receiver's repeat, as Media3's. */
internal fun repeatFor(repeatMode: @Player.RepeatMode Int, wholeQueueSent: Boolean): CastDevice.Repeat = when {
    repeatMode == Player.REPEAT_MODE_ONE -> CastDevice.Repeat.ONE
    repeatMode == Player.REPEAT_MODE_ALL && wholeQueueSent -> CastDevice.Repeat.ALL
    else -> CastDevice.Repeat.OFF
}
