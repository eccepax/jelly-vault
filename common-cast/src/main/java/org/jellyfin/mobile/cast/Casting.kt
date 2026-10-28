package org.jellyfin.mobile.cast

import android.content.Context
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.cast.Cast
import androidx.media3.cast.CastParams
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.MediaItemConverter
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import androidx.media3.common.PlayerTransferState
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
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

    /** [player]'s queue is emptied by [Casting.forgetCast] until the answer is called. */
    fun forgetsWithCast(player: Player): () -> Unit
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
    private val players = mutableSetOf<Player>()

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
        castContext.sessionManager.addSessionManagerListener(
            object : SessionManagerListener<CastSession> {
                override fun onSessionStarting(session: CastSession) {
                    connecting.value = session.deviceName
                }

                override fun onSessionStarted(session: CastSession, sessionId: String) {
                    connecting.value = null
                    device.value = session.deviceName
                }

                override fun onSessionStartFailed(session: CastSession, error: Int) {
                    connecting.value = null
                }

                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                    device.value = session.deviceName
                }

                override fun onSessionEnded(session: CastSession, error: Int) {
                    connecting.value = null
                    device.value = null
                }

                // A moment without the network: playback stays on the cast device (see
                // RemoteMediaClientDevice), so it's still the one named
                override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
                override fun onSessionEnding(session: CastSession) = Unit
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

    override fun stopCasting() {
        castContext?.sessionManager?.endCurrentSession(true)
    }

    override fun forgetCast() {
        players.toList().forEach {
            it.stop()
            it.clearMediaItems()
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
        return CastPlayer.Builder(context)
            .setLocalPlayer(local)
            .setRemotePlayer(remote)
            .setTransferCallback(PausedComingBack())
            .build()
    }

    override fun forgetsWithCast(player: Player): () -> Unit {
        players += player
        return { players -= player }
    }

    private val CastSession.deviceName: String? get() = castDevice?.friendlyName
}

/**
 * Playback carries over as Media3 does it, but back here it waits paused rather than starting out of
 * the phone's speaker. Cast again soon after (within [CARRY_ON_WITHIN_MS]) without playing here
 * meanwhile, it plays on there as it did.
 */
@UnstableApi
private class PausedComingBack(
    private val now: () -> Long = SystemClock::elapsedRealtime,
) : CastPlayer.TransferCallback {
    /** When playback came back here paused from a cast device that was playing. */
    private var pausedComingBackAt: Long? = null

    override fun transferState(sourcePlayer: Player, targetPlayer: Player) {
        val wasPlaying = sourcePlayer.playWhenReady && sourcePlayer.playbackState != Player.STATE_ENDED
        val toCast = targetPlayer.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
        val state = PlayerTransferState.fromPlayer(sourcePlayer)
        val items = state.mediaItems
        val from = state.currentMediaItemIndex
        if (toCast && items.size > CAST_QUEUE_LIMIT && from >= 0) {
            // More than the cast device takes in one message: the playing song and those after it,
            // which the app tops up as it plays (see CAST_QUEUE_LIMIT)
            state.buildUpon()
                .setMediaItems(items.subList(from, minOf(items.size, from + CAST_QUEUE_LIMIT)))
                .setCurrentMediaItemIndex(0)
                .build()
                .setToPlayer(targetPlayer)
        } else {
            CastPlayer.TransferCallback.DEFAULT.transferState(sourcePlayer, targetPlayer)
        }
        if (!toCast) {
            targetPlayer.playWhenReady = false
            pausedComingBackAt = if (wasPlaying) now() else null
        } else {
            val carryOn = pausedComingBackAt?.let { now() - it <= CARRY_ON_WITHIN_MS } == true
            if (carryOn) targetPlayer.playWhenReady = true
            pausedComingBackAt = null
        }
    }

    companion object {
        const val CARRY_ON_WITHIN_MS = 30_000L
    }
}
