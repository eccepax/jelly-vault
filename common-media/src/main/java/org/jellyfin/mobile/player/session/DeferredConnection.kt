package org.jellyfin.mobile.player.session

import com.google.common.util.concurrent.ListenableFuture
import timber.log.Timber
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

/**
 * Something reached through a connection that's still being made, in the apps a MediaController
 * binding to the playback service: usable at once. Commands [send] before the connection is up are
 * replayed in order once it is, just after [onConnected]; if it fails they're dropped and logged.
 */
class DeferredConnection<T : Any>(
    connection: ListenableFuture<out T>,
    callbackExecutor: Executor,
    private val onConnected: (T) -> Unit,
) {
    /** What it's connected to; null until then, or for good if connecting failed. */
    var connected: T? = null
        private set

    private var failed = false
    private val pending = mutableListOf<(T) -> Unit>()

    init {
        connection.addListener({ connect(connection) }, callbackExecutor)
    }

    private fun connect(connection: ListenableFuture<out T>) {
        val remote = try {
            connection.get()
        } catch (e: ExecutionException) {
            Timber.e(e, "Couldn't connect to the playback service")
            failed = true
            pending.clear()
            return
        }
        connected = remote
        onConnected(remote)
        pending.forEach { it(remote) }
        pending.clear()
    }

    fun send(command: (T) -> Unit) {
        val remote = connected
        when {
            remote != null -> command(remote)
            !failed -> pending += command
        }
    }
}
