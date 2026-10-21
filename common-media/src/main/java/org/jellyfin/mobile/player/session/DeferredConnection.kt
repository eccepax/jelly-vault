package org.jellyfin.mobile.player.session

import com.google.common.util.concurrent.ListenableFuture
import timber.log.Timber
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor

/**
 * Something reached through a connection that's still being made, in the apps a MediaController
 * binding to the playback service: usable at once. It connects when [send] a command (or asked to
 * [connect]); [sendIfConnected] is for commands that only mean something to a live connection, and
 * never starts one. Commands sent before the connection is up are replayed in order once it is,
 * just after [onConnected]. If connecting fails they're dropped and logged; if the connection is
 * lost ([disconnected]), so are any still waiting, and the next [send] connects again.
 */
class DeferredConnection<T : Any> private constructor(
    private val connect: () -> ListenableFuture<out T>,
    private val callbackExecutor: Executor,
    /** Lets go of a connection given up on while it was being made. */
    private val release: (T) -> Unit,
    /** Whether a failed connection is tried again; a single connection given can't be. */
    private val retries: Boolean,
    private val onConnected: (T) -> Unit,
) {
    constructor(
        connect: () -> ListenableFuture<out T>,
        callbackExecutor: Executor,
        release: (T) -> Unit = {},
        onConnected: (T) -> Unit,
    ) : this(connect, callbackExecutor, release, retries = true, onConnected)

    /** Connects at once, to [connection] alone: once that fails, every command is dropped. */
    constructor(connection: ListenableFuture<out T>, callbackExecutor: Executor, onConnected: (T) -> Unit) :
        this({ connection }, callbackExecutor, release = {}, retries = false, onConnected) {
        connect()
    }

    /** What it's connected to; null until then, and again once the connection is lost. */
    var connected: T? = null
        private set

    private var connecting: ListenableFuture<out T>? = null
    private var failed = false
    private val pending = mutableListOf<(T) -> Unit>()

    /** Starts connecting, unless it's connected or connecting already (or can't any more). */
    fun connect() {
        if (connected != null || connecting != null || failed) return
        val connection = connect.invoke()
        connecting = connection
        connection.addListener({ onConnection(connection) }, callbackExecutor)
    }

    /**
     * The connection was lost (or given up on while being made), and the commands still waiting for
     * it with it: the next [send] connects again.
     */
    fun disconnected() {
        connected = null
        connecting = null
        pending.clear()
    }

    private fun onConnection(connection: ListenableFuture<out T>) {
        val remote = try {
            connection.get()
        } catch (e: ExecutionException) {
            if (connection !== connecting) return
            Timber.e(e, "Couldn't connect to the playback service")
            connecting = null
            failed = !retries
            pending.clear()
            return
        }
        if (connection !== connecting) {
            release(remote) // given up on
            return
        }
        connecting = null
        connected = remote
        onConnected(remote)
        pending.forEach { it(remote) }
        pending.clear()
    }

    fun send(command: (T) -> Unit) {
        val remote = connected
        when {
            remote != null -> command(remote)
            failed -> Unit
            else -> {
                pending += command
                connect()
            }
        }
    }

    fun sendIfConnected(command: (T) -> Unit) {
        val remote = connected
        when {
            remote != null -> command(remote)
            connecting != null -> pending += command
        }
    }
}
