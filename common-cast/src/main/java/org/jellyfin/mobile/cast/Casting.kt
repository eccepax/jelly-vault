package org.jellyfin.mobile.cast

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/** Casting as an app sees it: whether it can cast here, and to what while it does. */
interface Casting {
    /** False where casting can't work (no Google Play services): no cast button then. */
    val isAvailable: Boolean

    /** The name of the cast device playback is on; null while it's on this device. */
    val castDevice: StateFlow<String?>

    /** Ends the cast: the cast device stops, and playback comes back here (see SessionCasting). */
    fun stopCasting()
}

/** Makes players for the cast device (see [SessionCasting]); kept in this module with Media3's unstable cast API. */
@UnstableApi
internal interface CastPlayers {
    fun newCastPlayer(converter: MediaItemConverter): CastPlayer
}

/** The app's casting: through the Cast framework where Google Play services can run it. */
@OptIn(UnstableApi::class)
fun casting(context: Context): Casting = castContextOrNull(context)?.let(::CastContextCasting) ?: NoCasting

/** Must be called on the main thread, like everything else in the Cast framework. */
private fun castContextOrNull(context: Context): CastContext? {
    val playServices = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    if (playServices != ConnectionResult.SUCCESS) return null
    // Not only IllegalStateException: Play services present but unusable (e.g. mid-update) throws
    // other runtime errors, and either way the app should simply not cast.
    @Suppress("TooGenericExceptionCaught")
    return try {
        @Suppress("DEPRECATION") // The Task-returning version only defers this; the app needs it now
        CastContext.getSharedInstance(context)
    } catch (e: RuntimeException) {
        Timber.w(e, "Cast isn't available")
        null
    }
}

/** Where casting can't work. */
object NoCasting : Casting {
    override val isAvailable = false
    override val castDevice: StateFlow<String?> = MutableStateFlow(null)
    override fun stopCasting() = Unit
}

@UnstableApi
private class CastContextCasting(private val castContext: CastContext) : Casting, CastPlayers {
    override val isAvailable = true

    private val device = MutableStateFlow(castContext.sessionManager.currentCastSession?.deviceName)
    override val castDevice: StateFlow<String?> = device.asStateFlow()

    init {
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
    }

    override fun stopCasting() = castContext.sessionManager.endCurrentSession(true)

    override fun newCastPlayer(converter: MediaItemConverter): CastPlayer = CastPlayer(castContext, converter)

    private val CastSession.deviceName: String? get() = castDevice?.friendlyName
}
