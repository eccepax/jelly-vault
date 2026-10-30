package org.jellyfin.mobile.cast

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.cast.Cast
import androidx.media3.cast.CastParams
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.MediaItemConverter
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.MediaQueue
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/** Casting as an app sees it: whether it can cast here, and to what while it does. */
interface Casting {
    /**
     * False where casting can't work (no Google Play services), and until the Cast framework has
     * started: no cast button then.
     */
    val isAvailable: StateFlow<Boolean>

    /** The name of the cast device playback is on; null while it's on this device. */
    val castDevice: StateFlow<String?>

    /** The cast device picked and still being connected to (its receiver starting); null otherwise. */
    val connectingTo: StateFlow<String?>

    /** Ends the cast: the cast device stops, and playback comes back here (see SessionCasting). */
    fun stopCasting()

    /**
     * Ends the cast and forgets what it was playing, so nothing of it comes back here: for signing
     * out, since the cast device holds stream urls with the token in them. Queues are emptied first,
     * in the playback services themselves, so it doesn't wait on a controller's stop arriving.
     */
    fun forgetCast()
}

/** A cast device's own player, and whether one is [connected] as it's made. */
internal class RemotePlayer(val player: Player, val connected: Boolean)

/** The cast device for playback services (see [SessionCasting]). */
@UnstableApi
internal interface CastPlayers {
    /**
     * Runs [onReady] once the Cast framework has started (at once if it has); never if it can't.
     * The answer takes it back while it's still waiting; null when nothing is (it ran, or never will).
     */
    fun whenReady(onReady: () -> Unit): (() -> Unit)?

    /**
     * Media3's player over [local] and the cast device, once the framework has started: playback
     * moves to the cast device while one is connected, and back to [local] (paused) after.
     */
    fun castPlayer(local: Player, converter: MediaItemConverter): Player

    /**
     * The cast device's own player, with no player here to move to: for an app that plays without a
     * media session (LiveVault). [onConnectionChanged] hears whenever a cast device connects or goes.
     */
    fun remoteCastPlayer(converter: MediaItemConverter, onConnectionChanged: (Boolean) -> Unit): RemotePlayer

    /**
     * [player]'s queue is emptied by [Casting.forgetCast] until the answer is called. [endingHere] is
     * told whenever the cast is ended from here; [forgotten] when it's forgotten, once emptied.
     */
    fun forgetsWithCast(player: Player, endingHere: () -> Unit = {}, forgotten: () -> Unit = {}): () -> Unit

    /**
     * [onFailure] is told whenever the cast device stops because it couldn't play what it was sent
     * (see [CastFailureWatch]), until the answer is called.
     */
    fun onCastFailure(onFailure: () -> Unit): () -> Unit
}

/**
 * The app's casting: through the Cast framework where Google Play services can run it. It starts
 * in the background (loading it takes a while, and the app's first screen shouldn't wait), so
 * casting is available a moment later. Called on the main thread, like the rest of the framework.
 */
@OptIn(UnstableApi::class)
fun casting(context: Context): Casting {
    val playServices = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    return if (playServices == ConnectionResult.SUCCESS) CastContextCasting(context.applicationContext) else NoCasting
}

/** Where casting can't work. */
object NoCasting : Casting {
    override val isAvailable: StateFlow<Boolean> = MutableStateFlow(false)
    override val castDevice: StateFlow<String?> = MutableStateFlow(null)
    override val connectingTo: StateFlow<String?> = MutableStateFlow(null)
    override fun stopCasting() = Unit
    override fun forgetCast() = Unit
}

@UnstableApi
private class CastContextCasting(private val context: Context) : Casting, CastPlayers {
    private var castContext: CastContext? = null
    private val gate = StartGate()

    /** What a cast player is told when the cast is ended from here, and when it's forgotten. */
    private class Told(val endingHere: () -> Unit, val forgotten: () -> Unit)

    private val players = mutableMapOf<Player, Told>()

    private val failureListeners = mutableListOf<() -> Unit>()
    private val failures = CastFailureWatch { failureListeners.toList().forEach { it() } }

    /** The cast device's media channel while one is connected, whose statuses tell of failures. */
    private var watched: RemoteMediaClient? = null
    private val statuses = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            val status = watched?.mediaStatus ?: return
            failures.onStatus(status.playerState, status.idleReason)
        }
    }

    private val available = MutableStateFlow(false)
    override val isAvailable: StateFlow<Boolean> = available.asStateFlow()

    private val device = MutableStateFlow<String?>(null)
    override val castDevice: StateFlow<String?> = device.asStateFlow()

    private val connecting = MutableStateFlow<String?>(null)
    override val connectingTo: StateFlow<String?> = connecting.asStateFlow()

    init {
        // Not only IllegalStateException: Play services present but unusable (e.g. mid-update) throws
        // other runtime errors, and either way the app should simply not cast.
        @Suppress("TooGenericExceptionCaught")
        try {
            CastContext.getSharedInstance(context, Dispatchers.IO.asExecutor())
                .addOnSuccessListener(::started)
                .addOnFailureListener {
                    Timber.w(it, "Cast isn't available")
                    gate.failed()
                }
        } catch (e: RuntimeException) {
            Timber.w(e, "Cast isn't available")
            gate.failed()
        }
    }

    override fun whenReady(onReady: () -> Unit) = gate.whenReady(onReady)

    /** On the main thread, where the Task delivers it. */
    private fun started(castContext: CastContext) {
        this.castContext = castContext
        device.value = castContext.sessionManager.currentCastSession?.deviceName
        watch(castContext.sessionManager.currentCastSession)
        castContext.sessionManager.addSessionManagerListener(
            object : SessionManagerListener<CastSession> {
                override fun onSessionStarting(session: CastSession) {
                    connecting.value = session.deviceName
                }

                override fun onSessionStarted(session: CastSession, sessionId: String) {
                    connecting.value = null
                    device.value = session.deviceName
                    watch(session)
                }

                override fun onSessionStartFailed(session: CastSession, error: Int) {
                    connecting.value = null
                }

                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                    device.value = session.deviceName
                    watch(session)
                }

                override fun onSessionEnded(session: CastSession, error: Int) {
                    connecting.value = null
                    device.value = null
                    watch(null)
                }

                // A moment without the network: playback stays on the cast device (see
                // RemoteMediaClientDevice), so it's still the one named
                override fun onSessionSuspended(session: CastSession, reason: Int) = Unit

                // Ended from this phone however it's done (the cast button's dialog, the system's
                // output switcher, as well as stopCasting), before the cast device stops on the way out
                override fun onSessionEnding(session: CastSession) {
                    players.values.toList().forEach { it.endingHere() }
                }
                override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
                override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
            },
            CastSession::class.java,
        )
        // Media3's cast players work through its own hold on the framework, which finds the one started here
        Cast.getSingletonInstance(context).initialize(
            CastParams.Builder()
                .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
                .build(),
        )
        available.value = true
        gate.started()
    }

    private fun watch(session: CastSession?) {
        val client = session?.remoteMediaClient
        if (client === watched) return
        watched?.unregisterCallback(statuses)
        failures.reset()
        watched = client
        client?.registerCallback(statuses)
    }

    override fun stopCasting() {
        players.values.toList().forEach { it.endingHere() }
        castContext?.sessionManager?.endCurrentSession(true)
    }

    override fun forgetCast() {
        players.toList().forEach { (player, told) ->
            told.endingHere()
            player.stop()
            player.clearMediaItems()
            told.forgotten()
        }
        stopCasting()
    }

    override fun castPlayer(local: Player, converter: MediaItemConverter): Player {
        // Skips go as far there as here (AudioVault's 10 s back and 30 s forward, say)
        val remote = RemoteCastPlayer.Builder(context)
            .setMediaItemConverter(converter)
            .setSeekBackIncrementMs(local.seekBackIncrement)
            .setSeekForwardIncrementMs(local.seekForwardIncrement)
            .build()
        val sent = (converter as? VaultMediaItemConverter)?.sent
        val player = CastPlayer.Builder(context)
            .setLocalPlayer(local)
            .setRemotePlayer(remote)
            // Nothing sent yet (the app started again mid-cast), what's there is taken as its own
            .setTransferCallback(
                PausedComingBack(sentFromHere = { sent == null || sent.isEmpty || sent[it.mediaId] != null }),
            )
            .build()
        // Media3's cast player never has an error of its own: one the cast device has is given it here
        val reporting = CastFailureReporting(player)
        reporting.onRelease = onCastFailure(reporting::failedThere)
        val guarded = CastSeekGuard(reporting) { remote.currentTimeline.isEmpty }
        if (sent == null) return guarded
        return CastQueueDetails(guarded, sent, SessionQueueLookup())
    }

    /** The connected cast device's queue, as the Cast framework fetches it a few songs at a time. */
    private inner class SessionQueueLookup : CastQueueLookup {
        private var changed: () -> Unit = {}
        private var watchedQueue: MediaQueue? = null
        private val fetched = object : MediaQueue.Callback() {
            override fun itemsUpdatedAtIndexes(indexes: IntArray) = changed()
        }

        override fun contentIdOf(itemId: Int): String? {
            val queue = queue() ?: return null
            val index = queue.indexOfItemWithId(itemId).takeIf { it >= 0 } ?: return null
            return queue.getItemAtIndex(index, true)?.media?.contentId
        }

        override fun onChanged(changed: () -> Unit) {
            this.changed = changed
        }

        /** The connected cast device's queue, heard as its songs are fetched. */
        private fun queue(): MediaQueue? {
            val queue = castContext?.sessionManager?.currentCastSession?.remoteMediaClient?.mediaQueue
            if (queue !== watchedQueue) {
                watchedQueue?.unregisterCallback(fetched)
                queue?.registerCallback(fetched)
                watchedQueue = queue
            }
            return queue
        }
    }

    override fun remoteCastPlayer(converter: MediaItemConverter, onConnectionChanged: (Boolean) -> Unit): RemotePlayer {
        val player = RemoteCastPlayer.Builder(context).setMediaItemConverter(converter).build()
        player.setSessionAvailabilityListener(
            object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() = onConnectionChanged(true)

                override fun onCastSessionUnavailable() = onConnectionChanged(false)
            },
        )
        return RemotePlayer(player, player.isCastSessionAvailable)
    }

    override fun forgetsWithCast(player: Player, endingHere: () -> Unit, forgotten: () -> Unit): () -> Unit {
        players[player] = Told(endingHere, forgotten)
        return { players -= player }
    }

    override fun onCastFailure(onFailure: () -> Unit): () -> Unit {
        failureListeners += onFailure
        return { failureListeners -= onFailure }
    }

    private val CastSession.deviceName: String? get() = castDevice?.friendlyName
}

/**
 * Playback carries over as Media3 does it, but back here it waits paused rather than starting out of
 * the phone's speaker. Cast again soon after (within [CARRY_ON_WITHIN_MS]) without playing here
 * meanwhile, it plays on there as it did. A cast device playing a song this app didn't send (another
 * app took it over, see [sentFromAnotherApp]) brings nothing back: the player here is left empty.
 */
@UnstableApi
internal class PausedComingBack(
    private val now: () -> Long = SystemClock::elapsedRealtime,
    /** Whether this app sent the cast device [item]. */
    private val sentFromHere: (item: MediaItem) -> Boolean = { true },
) : CastPlayer.TransferCallback {
    /** When playback came back here paused from a cast device that was playing. */
    private var pausedComingBackAt: Long? = null

    /** The player playback came back to, heard until it's played here or cast again. */
    private var cameBackTo: Player? = null
    private val playedHere = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            // The player's own value, not the event's: this transfer's own changes may arrive late
            if (cameBackTo?.playWhenReady == true) {
                pausedComingBackAt = null
                stopHearing()
            }
        }
    }

    override fun transferState(sourcePlayer: Player, targetPlayer: Player) {
        val fromCast = sourcePlayer.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
        val playingThere = sourcePlayer.currentMediaItem
        if (fromCast && playingThere != null && !sentFromHere(playingThere)) {
            stopHearing()
            pausedComingBackAt = null
            targetPlayer.playWhenReady = false
            targetPlayer.stop()
            targetPlayer.clearMediaItems()
            return
        }
        val wasPlaying = sourcePlayer.playWhenReady && sourcePlayer.playbackState != Player.STATE_ENDED
        val toCast = targetPlayer.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
        val state = PlayerTransferState.fromPlayer(sourcePlayer)
        val items = state.mediaItems
        val from = state.currentMediaItemIndex
        if (toCast && items.size > CAST_QUEUE_LIMIT && from >= 0) {
            // More than the cast device takes in one message: the playing song and those after it,
            // which the app tops up as it plays (see CAST_QUEUE_LIMIT)
            state.buildUpon()
                .setMediaItems(items.castWindowFrom(from))
                .setCurrentMediaItemIndex(0)
                .build()
                .setToPlayer(targetPlayer)
        } else {
            CastPlayer.TransferCallback.DEFAULT.transferState(sourcePlayer, targetPlayer)
        }
        stopHearing()
        if (!toCast) {
            targetPlayer.playWhenReady = false
            pausedComingBackAt = if (wasPlaying) now() else null
            // Played here meanwhile (and paused again, or it would carry on anyway), casting again
            // goes as it was left here
            if (wasPlaying) {
                cameBackTo = targetPlayer
                targetPlayer.addListener(playedHere)
            }
        } else {
            val carryOn = pausedComingBackAt?.let { now() - it <= CARRY_ON_WITHIN_MS } == true
            if (carryOn) targetPlayer.playWhenReady = true
            pausedComingBackAt = null
        }
    }

    private fun stopHearing() {
        cameBackTo?.removeListener(playedHere)
        cameBackTo = null
    }

    companion object {
        const val CARRY_ON_WITHIN_MS = 30_000L
    }
}
