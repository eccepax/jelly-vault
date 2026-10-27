package org.jellyfin.mobile.cast

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.C
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
}

/** Makes players for the cast device (see [SessionCasting]); kept in this module with Media3's unstable cast API. */
@UnstableApi
internal interface CastPlayers {
    /** Runs [onReady] once the Cast framework has started (at once if it has); never if it can't. */
    fun whenReady(onReady: () -> Unit)

    fun newCastPlayer(converter: MediaItemConverter): CastPlayer
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
}

@UnstableApi
private class CastContextCasting(private val context: Context) : Casting, CastPlayers {
    private var castContext: CastContext? = null
    private val gate = StartGate()

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
        castContext?.sessionManager?.endCurrentSession(true)
    }

    /**
     * With the context, the player names the system's routing session for the cast (see the
     * MediaTransferReceiver in the manifest), so the volume keys and the system's volume panel
     * reach the cast device; without it, Android says to use the cast device's remote instead.
     */
    override fun newCastPlayer(converter: MediaItemConverter): CastPlayer = CastPlayer(
        context,
        checkNotNull(castContext) { "The Cast framework hasn't started" },
        converter,
        C.DEFAULT_SEEK_BACK_INCREMENT_MS,
        C.DEFAULT_SEEK_FORWARD_INCREMENT_MS,
        C.DEFAULT_MAX_SEEK_TO_PREVIOUS_POSITION_MS,
    )

    private val CastSession.deviceName: String? get() = castDevice?.friendlyName
}
