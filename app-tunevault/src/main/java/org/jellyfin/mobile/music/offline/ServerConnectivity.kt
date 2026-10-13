package org.jellyfin.mobile.music.offline

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import timber.log.Timber
import java.io.IOException

/**
 * Whether the Vault can be reached. Reads go to the server until one fails for want of a network;
 * from then on they go straight to the downloads (no waiting on timeouts screen after screen), and
 * the server is checked in the background until it answers again.
 */
class ServerConnectivity(
    private val ping: suspend () -> Boolean,
    private val scope: CoroutineScope,
) {
    private val _isOffline = MutableStateFlow(false)
    val isOffline: StateFlow<Boolean> = _isOffline.asStateFlow()

    suspend fun <T> choose(online: suspend () -> T, offline: suspend () -> T): T {
        if (_isOffline.value) return offline()
        return try {
            online()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            if (!e.isNetworkFailure()) throw e
            Timber.i(e, "The Vault can't be reached; using downloads")
            goOffline()
            offline()
        }
    }

    private fun goOffline() {
        if (!_isOffline.compareAndSet(expect = false, update = true)) return
        scope.launch {
            while (_isOffline.value) {
                delay(RETRY_INTERVAL_MS)
                val reachable = try {
                    ping()
                } catch (e: CancellationException) {
                    throw e
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    false
                }
                if (reachable) _isOffline.value = false
            }
        }
    }

    companion object {
        const val RETRY_INTERVAL_MS = 15_000L
    }
}

/** No route to the server, as opposed to the server answering with an error. */
fun Throwable.isNetworkFailure(): Boolean = when (this) {
    is InvalidStatusException -> false
    is IOException, is ApiClientException -> true
    else -> false
}
