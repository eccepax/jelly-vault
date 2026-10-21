package org.jellyfin.mobile.player.session

import com.google.common.util.concurrent.ListenableFuture
import timber.log.Timber
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

/**
 * Something reached through a connection that's still being made, in the apps a MediaController
 * binding to the playback service: usable at once. It connects when first [send] a command (or
 * asked to [connect]); commands sent before the connection is up are replayed in order once it is,
 * just after [onConnected]. If connecting fails they're dropped and logged, and if the connection is
 * lost ([disconnected]), the next command connects again.
 */
class DeferredConnection<T : Any>(
    private val connect: () -> ListenableFuture<out T>,
    private val callbackExecutor: Executor,
    private val onConnected: (T) -> Unit,
) {
    /** Connects at once, to [connection] alone: once that fails, every command is dropped. */
    constructor(connection: ListenableFuture<out T>, callbackExecutor: Executor, onConnected: (T) -> Unit) :
        this({ connection }, callbackExecutor, onConnected) {
        connect()
    }

    /** What it's connected to; null until then, and again once the connection is lost. */
    var connected: T? = null
        private set

    private var connecting: ListenableFuture<out T>? = null
    private val pending = mutableListOf<(T) -> Unit>()

    /** Starts connecting, unless it's connected or connecting already. */
    fun connect() {
        if (connected != null || connecting != null) return
        val connection = connect.invoke()
        connecting = connection
        connection.addListener({ onConnection(connection) }, callbackExecutor)
    }

    /** The connection was lost (or given up on while being made): the next command connects again. */
    fun disconnected() {
        connected = null
        connecting = null
    }

    private fun onConnection(connection: ListenableFuture<out T>) {
        if (connection !== connecting) return // given up on
        connecting = null
        val remote = try {
            connection.get()
        } catch (e: ExecutionException) {
            Timber.e(e, "Couldn't connect to the playback service")
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
        if (remote != null) {
            command(remote)
        } else {
            pending += command
            connect()
        }
    }
}
