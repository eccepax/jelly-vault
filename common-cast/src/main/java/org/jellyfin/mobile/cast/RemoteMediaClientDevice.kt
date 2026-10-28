package org.jellyfin.mobile.cast

import android.content.Context
import android.media.MediaRouter2
import android.media.RouteDiscoveryPreference
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.Cast
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaQueueData
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.MediaQueue
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.PendingResult
import org.jellyfin.mobile.cast.CastDevice.Repeat
import org.jellyfin.mobile.cast.DeviceStatus.IdleReason
import org.jellyfin.mobile.cast.DeviceStatus.State
import timber.log.Timber
import kotlin.math.roundToInt

/**
 * A cast device through the Cast framework: the session the framework has up, its media channel
 * (RemoteMediaClient) for the queue and playback, and the session for the volume. Each item goes
 * with the key of its entry (see [CastQueueItems]), which the cast device sends back, so its items
 * are known by key; the queue's item ids come from the framework's MediaQueue, which fetches items
 * it hasn't seen ([DeviceStatus.queueKnown] is false until it has). What it says is put together
 * once each time it says something, and read from there. A [live] device sends items as live streams.
 *
 * A session suspended (the network gone for a moment) isn't a session ended: commands wait for it
 * to come back, and then it's connected again (see [CastDevice.Listener.onConnectionChanged]), so
 * playback stays on the cast device rather than coming back here and going out again.
 */
@UnstableApi
internal class RemoteMediaClientDevice(
    private val context: Context,
    private val castContext: CastContext,
    private val live: Boolean = false,
) : CastDevice {

    private val handler = Handler(Looper.getMainLooper())
    private var session: CastSession? = null
    private var suspended = false
    private val client: RemoteMediaClient? get() = session?.takeUnless { suspended }?.remoteMediaClient

    /** Keys of the items this session has seen, by their item id: the queue's items aren't all in each report. */
    private val keyById = mutableMapOf<Int, String>()

    /** The queue's item ids as last said, in the order of [status]'s keys. */
    private var ids = IntArray(0)

    override var listener: CastDevice.Listener? = null

    override val isConnected: Boolean get() = client != null

    override var status: DeviceStatus? = null
        private set

    private val routing: Routing? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Routing(context) else null

    override val deviceInfo: DeviceInfo
        get() = DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE)
            .setMaxVolume(CastDevice.MAX_VOLUME)
            .setRoutingControllerId(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) routing?.controllerId() else null,
            )
            .build()

    // Not every session can say (one mid-teardown throws): no volume then
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override val volume: Int
        get() = try {
            ((session?.volume ?: 0.0) * CastDevice.MAX_VOLUME).roundToInt()
        } catch (e: IllegalStateException) {
            0
        }

    @Suppress("SwallowedException")
    override val isMuted: Boolean
        get() = try {
            session?.isMute ?: false
        } catch (e: IllegalStateException) {
            false
        }

    private val clientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = reported()
        override fun onQueueStatusUpdated() = reported()
        override fun onMetadataUpdated() = reported()
    }

    private val queueCallback = object : MediaQueue.Callback() {
        override fun mediaQueueChanged() = reported()
        override fun itemsInsertedInRange(insertIndex: Int, insertCount: Int) = reported()
        override fun itemsRemovedAtIndexes(indexes: IntArray) = reported()
        override fun itemsReorderedAtIndexes(indexes: List<Int>, insertBeforeIndex: Int) = reported()
        override fun itemsUpdatedAtIndexes(indexes: IntArray) = reported()
    }

    private val castListener = object : Cast.Listener() {
        override fun onVolumeChanged() {
            listener?.onVolumeChanged()
        }
    }

    private val sessions = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = attach(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = attach(session)

        // One replaced already (suspended, then back as another) ending late isn't the one attached ending
        override fun onSessionEnded(session: CastSession, error: Int) {
            if (this@RemoteMediaClientDevice.session === session) detach(notify = true)
        }
        override fun onSessionSuspended(session: CastSession, reason: Int) {
            if (this@RemoteMediaClientDevice.session === session) suspended = true
        }
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
    }

    init {
        castContext.sessionManager.addSessionManagerListener(sessions, CastSession::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) routing?.start { listener?.onVolumeChanged() }
        castContext.sessionManager.currentCastSession?.let(::attach)
    }

    private fun attach(session: CastSession) {
        if (this.session === session) {
            if (suspended) resumed(session)
            return
        }
        val client = session.remoteMediaClient
        // Back from a suspended session as another one: the same cast carrying on, not one ending,
        // unless the new one has no media channel to carry on with
        detach(notify = !suspended || client == null)
        if (client == null) return
        this.session = session
        keyById.clear()
        client.registerCallback(clientCallback)
        client.mediaQueue.registerCallback(queueCallback)
        session.addCastListener(castListener)
        connected(session, client)
    }

    /** The session is back: what it holds is asked for again, and the commands lost meanwhile go again. */
    private fun resumed(session: CastSession) {
        suspended = false
        val client = session.remoteMediaClient ?: return detach(notify = true)
        connected(session, client)
    }

    private fun connected(session: CastSession, client: RemoteMediaClient) {
        status = null
        listener?.onConnectionChanged(true)
        // What it holds now, before anything is sent: it may still be playing what this app sent before
        client.requestStatus().setResultCallback { if (this.session === session && !suspended) reported() }
    }

    private fun detach(notify: Boolean) {
        val session = session ?: return
        session.remoteMediaClient?.let {
            it.unregisterCallback(clientCallback)
            it.mediaQueue.unregisterCallback(queueCallback)
        }
        session.removeCastListener(castListener)
        this.session = null
        suspended = false
        status = null
        ids = IntArray(0)
        if (notify) listener?.onConnectionChanged(false)
    }

    private fun reported() {
        val client = client ?: return
        val media = client.mediaStatus
        val said = if (media != null) {
            statusOf(client, media)
        } else {
            ids = IntArray(0)
            EMPTY.copy(atMs = SystemClock.elapsedRealtime())
        }
        status = said
        listener?.onStatus(said)
    }

    private fun statusOf(client: RemoteMediaClient, media: MediaStatus): DeviceStatus {
        media.queueItems.forEach { learn(it) }
        val queue = client.mediaQueue
        val queueIds = queue.itemIds
        val currentId = media.currentItemId
        val ids = queueIds.takeIf { it.isNotEmpty() } ?: media.queueItems.map { it.itemId }.toIntArray()
        this.ids = ids
        var known = !isBehind(queueIds, media)
        ids.forEachIndexed { index, id ->
            val item = media.getQueueItemById(id) ?: queue.getItemAtIndex(index, true)
            if (item == null && id !in keyById) known = false
            item?.let(::learn)
        }
        val currentItem = media.getQueueItemById(currentId)
            ?: queue.indexOfItemWithId(currentId).takeIf { it >= 0 }?.let { queue.getItemAtIndex(it, true) }
        currentItem?.let(::learn)
        val currentKey = keyById[currentId]
        // Playing an item it hasn't said the details of yet: whose it is waits until it has
        if (currentId != MediaQueueItem.INVALID_ITEM_ID && currentItem == null && currentKey == null) known = false
        return DeviceStatus(
            keys = ids.map { keyById[it] },
            currentKey = currentKey,
            playsOther = media.mediaInfo != null && currentItem != null && currentKey == null,
            state = stateOf(media.playerState),
            idleReason = idleReasonOf(media.idleReason),
            positionMs = client.approximateStreamPosition,
            atMs = SystemClock.elapsedRealtime(),
            speed = media.playbackRate.toFloat(),
            durationMs = media.mediaInfo?.streamDuration?.takeIf { it > 0 } ?: C.TIME_UNSET,
            repeat = repeatOf(media.queueRepeatMode),
            queueKnown = known,
        )
    }

    /**
     * Whether the framework's queue lists items from before the last load, which it does until it
     * fetches them again: what the cast device holds isn't known until then (it says again once it has).
     */
    private fun isBehind(queueIds: IntArray, media: MediaStatus): Boolean {
        if (queueIds.isEmpty()) return false
        val currentId = media.currentItemId
        if (currentId != MediaQueueItem.INVALID_ITEM_ID && currentId !in queueIds) return true
        return media.queueItems.any { it.itemId !in queueIds }
    }

    override fun heldItems(): List<SentItem?> {
        val client = client ?: return emptyList()
        val media = client.mediaStatus
        return ids.mapIndexed { index, id ->
            val item = media?.getQueueItemById(id) ?: client.mediaQueue.getItemAtIndex(index, false)
            item?.let(CastQueueItems::sentItemOf)
        }
    }

    private fun stateOf(playerState: Int) = when (playerState) {
        MediaStatus.PLAYER_STATE_PLAYING -> State.PLAYING
        MediaStatus.PLAYER_STATE_PAUSED -> State.PAUSED
        MediaStatus.PLAYER_STATE_BUFFERING, MediaStatus.PLAYER_STATE_LOADING -> State.BUFFERING
        else -> State.IDLE
    }

    private fun idleReasonOf(idleReason: Int) = when (idleReason) {
        MediaStatus.IDLE_REASON_FINISHED -> IdleReason.FINISHED
        MediaStatus.IDLE_REASON_CANCELED -> IdleReason.CANCELED
        MediaStatus.IDLE_REASON_INTERRUPTED -> IdleReason.INTERRUPTED
        MediaStatus.IDLE_REASON_ERROR -> IdleReason.ERROR
        else -> IdleReason.NONE
    }

    private fun repeatOf(repeatMode: Int) = when (repeatMode) {
        MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> Repeat.ONE
        MediaStatus.REPEAT_MODE_REPEAT_ALL, MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> Repeat.ALL
        else -> Repeat.OFF
    }

    private fun learn(item: MediaQueueItem) {
        CastQueueItems.keyOf(item)?.let { keyById[item.itemId] = it }
    }

    private fun queueItemOf(sent: SentItem) = CastQueueItems.queueItemOf(sent, live)

    /** The item with [key] in the queue as last said: a key sent again has a new id, and the old one is gone. */
    private fun idOf(key: String): Int? = ids.firstOrNull { keyById[it] == key }

    override fun load(
        items: List<SentItem>,
        startIndex: Int,
        startMs: Long,
        playWhenReady: Boolean,
        repeat: Repeat,
        speed: Float,
        done: (Boolean) -> Unit,
    ) = send(done) {
        val queue = MediaQueueData.Builder()
            .setItems(items.map(::queueItemOf))
            .setStartIndex(startIndex)
            .setRepeatMode(repeatModeOf(repeat))
            .build()
        it.load(
            MediaLoadRequestData.Builder()
                .setQueueData(queue)
                .setAutoplay(playWhenReady)
                .setCurrentTime(startMs)
                .setPlaybackRate(speed.toDouble())
                .build(),
        )
    }

    override fun append(items: List<SentItem>, done: (Boolean) -> Unit) = send(done) {
        it.queueInsertItems(items.map(::queueItemOf).toTypedArray(), MediaQueueItem.INVALID_ITEM_ID, null)
    }

    override fun keepOnly(keys: Set<String>, done: (Boolean) -> Unit) {
        if (client == null) return later(done, false)
        // The ids its status was made from, so what goes is what its keys said was there
        val ids = ids.filter { keyById[it] !in keys }
        if (ids.isEmpty()) return later(done, true)
        send(done) { it.queueRemoveItems(ids.toIntArray(), null) }
    }

    override fun jumpTo(key: String, startMs: Long, done: (Boolean) -> Unit) {
        val id = idOf(key) ?: return later(done, false)
        send(done) { it.queueJumpToItem(id, startMs, null) }
    }

    override fun seek(positionMs: Long, done: (Boolean) -> Unit) =
        send(done) { it.seek(MediaSeekOptions.Builder().setPosition(positionMs).build()) }

    override fun play(done: (Boolean) -> Unit) = send(done) { it.play() }
    override fun pause(done: (Boolean) -> Unit) = send(done) { it.pause() }
    override fun setSpeed(speed: Float, done: (Boolean) -> Unit) = send(done) { it.setPlaybackRate(speed.toDouble()) }
    override fun setRepeat(repeat: Repeat, done: (Boolean) -> Unit) =
        send(done) { it.queueSetRepeatMode(repeatModeOf(repeat), null) }
    override fun stop(done: (Boolean) -> Unit) = send(done) { it.stop() }

    @Suppress("TooGenericExceptionCaught")
    override fun setVolume(volume: Int) {
        try {
            session?.volume = volume.toDouble() / CastDevice.MAX_VOLUME
        } catch (e: Exception) {
            Timber.w(e, "Couldn't set the cast device's volume")
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override fun setMuted(muted: Boolean) {
        try {
            session?.isMute = muted
        } catch (e: Exception) {
            Timber.w(e, "Couldn't mute the cast device")
        }
    }

    override fun disconnect() {
        castContext.sessionManager.endCurrentSession(true)
    }

    override fun release() {
        castContext.sessionManager.removeSessionManagerListener(sessions, CastSession::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) routing?.stop()
        detach(notify = false)
    }

    /** Sends a command on this session's media channel; its answer, if this session is still up. */
    @Suppress("TooGenericExceptionCaught")
    private fun send(
        done: (Boolean) -> Unit,
        command: (RemoteMediaClient) -> PendingResult<RemoteMediaClient.MediaChannelResult>,
    ) {
        val session = session
        val client = client ?: return later(done, false)
        try {
            command(client).setResultCallback { result ->
                if (this.session !== session) return@setResultCallback
                if (!result.status.isSuccess) Timber.w("Cast command failed: %s", result.status)
                done(result.status.isSuccess)
            }
        } catch (e: Exception) {
            Timber.w(e, "Cast command not sent")
            later(done, false)
        }
    }

    /** Answers on the next turn of the main looper, like the framework does. */
    private fun later(done: (Boolean) -> Unit, ok: Boolean) {
        handler.post { done(ok) }
    }

    private fun repeatModeOf(repeat: Repeat) = when (repeat) {
        Repeat.OFF -> MediaStatus.REPEAT_MODE_REPEAT_OFF
        Repeat.ONE -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
        Repeat.ALL -> MediaStatus.REPEAT_MODE_REPEAT_ALL
    }

    /**
     * The system's routing session for the cast (MediaRouter2, with the MediaTransferReceiver in the
     * manifest): named in [deviceInfo], the volume keys and the system's volume panel reach the cast
     * device. As Media3's cast player finds it: the second controller, after the system's own.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private class Routing(context: Context) {
        private val router = MediaRouter2.getInstance(context)
        private val handler = Handler(Looper.getMainLooper())
        private var transfers: MediaRouter2.TransferCallback? = null
        private val routes = object : MediaRouter2.RouteCallback() {}

        fun controllerId(): String? = router.controllers.takeIf { it.size == 2 }?.get(1)?.id

        fun start(onChange: () -> Unit) {
            val callback = object : MediaRouter2.TransferCallback() {
                override fun onTransfer(old: MediaRouter2.RoutingController, new: MediaRouter2.RoutingController) =
                    onChange()

                override fun onStop(controller: MediaRouter2.RoutingController) = onChange()
            }
            transfers = callback
            router.registerTransferCallback(handler::post, callback)
            // Without a route callback, the router doesn't keep its controllers up to date
            router.registerRouteCallback(
                handler::post,
                routes,
                RouteDiscoveryPreference.Builder(emptyList(), false).build(),
            )
        }

        fun stop() {
            transfers?.let(router::unregisterTransferCallback)
            router.unregisterRouteCallback(routes)
            handler.removeCallbacksAndMessages(null)
        }
    }

    private companion object {
        val EMPTY = DeviceStatus(
            keys = emptyList(),
            currentKey = null,
            playsOther = false,
            state = State.IDLE,
            idleReason = IdleReason.NONE,
            positionMs = 0L,
            atMs = 0L,
            speed = 1f,
            durationMs = C.TIME_UNSET,
        )
    }
}
