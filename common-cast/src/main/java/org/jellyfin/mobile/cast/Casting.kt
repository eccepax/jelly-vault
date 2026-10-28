package org.jellyfin.mobile.cast

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
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
     * The answer takes it back while it's still waiting.
     */
    fun whenReady(onReady: () -> Unit): () -> Unit

    /** The cast device, through the framework once it has started; [live] sends items as live streams. */
    fun newDevice(live: Boolean): CastDevice

    /** [player]'s queue is emptied by [Casting.forgetCast] until the answer is called. */
    fun forgetsWithCast(player: CastQueuePlayer, endingHere: () -> Unit = {}): () -> Unit
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
    override fun stopCasting() = Unit
    override fun forgetCast() = Unit
}

@UnstableApi
private class CastContextCasting(private val context: Context) : Casting, CastPlayers {
    private var castContext: CastContext? = null
    private val gate = StartGate()

    /** The cast players, each with what it's told when the cast is ended from here. */
    private val players = mutableMapOf<CastQueuePlayer, () -> Unit>()

    private val available = MutableStateFlow(false)
    override val isAvailable: StateFlow<Boolean> = available.asStateFlow()

    private val device = MutableStateFlow<String?>(null)
    override val castDevice: StateFlow<String?> = device.asStateFlow()

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
                override fun onSessionStarted(session: CastSession, sessionId: String) {
                    device.value = session.deviceName
                }

                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                    device.value = session.deviceName
                }

                override fun onSessionEnded(session: CastSession, error: Int) {
                    device.value = null
                }

                // A moment without the network: playback stays on the cast device (see
                // RemoteMediaClientDevice), so it's still the one named
                override fun onSessionSuspended(session: CastSession, reason: Int) = Unit
                override fun onSessionStarting(session: CastSession) = Unit
                override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
                override fun onSessionEnding(session: CastSession) = Unit
                override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
                override fun onSessionResumeFailed(session: CastSession, error: Int) = Unit
            },
            CastSession::class.java,
        )
        available.value = true
        gate.started()
    }

    override fun stopCasting() {
        // Told first: the cast device stops on the way out, and that stop is this app's, not its remote's
        players.values.toList().forEach { it() }
        castContext?.sessionManager?.endCurrentSession(true)
    }

    override fun forgetCast() {
        players.toList().forEach { (player, endingHere) ->
            endingHere()
            player.stop()
            player.clearMediaItems()
        }
        stopCasting()
    }

    override fun newDevice(live: Boolean): CastDevice =
        RemoteMediaClientDevice(context, checkNotNull(castContext) { "The Cast framework hasn't started" }, live)

    override fun forgetsWithCast(player: CastQueuePlayer, endingHere: () -> Unit): () -> Unit {
        players[player] = endingHere
        return { players -= player }
    }

    private val CastSession.deviceName: String? get() = castDevice?.friendlyName
}
