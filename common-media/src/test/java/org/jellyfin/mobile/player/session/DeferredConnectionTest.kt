package org.jellyfin.mobile.player.session

import com.google.common.util.concurrent.ForwardingListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.Executor

class DeferredConnectionTest {

    private class Remote {
        val calls = mutableListOf<String>()
    }

    /** Each connection asked for, in order; the test completes them. */
    private val attempts = mutableListOf<SettableFuture<Remote>>()
    private val future get() = attempts.last()
    private val connectedTo = mutableListOf<Remote>()
    private val released = mutableListOf<Remote>()
    private val connection = DeferredConnection(
        connect = { SettableFuture.create<Remote>().also { attempts += it } },
        callbackExecutor = MoreExecutors.directExecutor(),
        release = { released += it },
    ) { connectedTo += it }

    @Test
    fun `nothing connects until it's needed`() {
        attempts.shouldBeEmpty()
    }

    @Test
    fun `commands sent before the connection are replayed in order once it's up`() {
        connection.send { it.calls += "first" }
        connection.send { it.calls += "second" }
        val remote = Remote()

        future.set(remote)

        attempts.size shouldBe 1
        remote.calls shouldBe listOf("first", "second")
    }

    @Test
    fun `commands after the connection go straight through`() {
        connection.connect()
        val remote = Remote()
        future.set(remote)

        connection.send { it.calls += "now" }

        remote.calls shouldBe listOf("now")
    }

    @Test
    fun `connecting while connecting or connected does nothing more`() {
        connection.connect()
        connection.connect()
        future.set(Remote())

        connection.connect()

        attempts.size shouldBe 1
    }

    @Test
    fun `it says when it's connected, before replaying anything`() {
        val remote = Remote()
        var connectedWhenReplayed: Boolean? = null
        connection.send { connectedWhenReplayed = connectedTo.isNotEmpty() }

        future.set(remote)

        connectedTo shouldBe listOf(remote)
        connectedWhenReplayed shouldBe true
        connection.connected shouldBe remote
    }

    @Test
    fun `when the connection fails, its commands are dropped rather than kept forever`() {
        connection.send { it.calls += "lost" }

        future.setException(IOException("service gone"))

        connectedTo.shouldBeEmpty()
        connection.connected shouldBe null
    }

    @Test
    fun `after a failed connection, the next command tries again`() {
        connection.send { it.calls += "lost" }
        future.setException(IOException("service gone"))

        connection.send { it.calls += "retried" }
        val remote = Remote()
        future.set(remote)

        attempts.size shouldBe 2
        remote.calls shouldBe listOf("retried")
    }

    @Test
    fun `after the connection is lost, the next command connects again`() {
        connection.connect()
        future.set(Remote())

        connection.disconnected()
        connection.connected shouldBe null
        connection.send { it.calls += "again" }
        val remote = Remote()
        future.set(remote)

        attempts.size shouldBe 2
        connectedTo.last() shouldBe remote
        remote.calls shouldBe listOf("again")
    }

    @Test
    fun `a connection given up on while it's still being made is released when it comes`() {
        connection.connect()
        val abandoned = future

        connection.disconnected()
        connection.connect()
        val late = Remote()
        abandoned.set(late)

        connectedTo.shouldBeEmpty()
        released shouldBe listOf(late)
        attempts.size shouldBe 2
    }

    @Test
    fun `commands waiting when the connection is lost are dropped, not replayed on the next one`() {
        connection.send { it.calls += "stale" }

        connection.disconnected()
        connection.send { it.calls += "fresh" }
        val remote = Remote()
        future.set(remote)

        remote.calls shouldBe listOf("fresh")
    }

    @Test
    fun `a command only for a live connection doesn't connect`() {
        connection.sendIfConnected { it.calls += "dropped" }

        attempts.shouldBeEmpty()
    }

    @Test
    fun `a command only for a live connection waits for one being made, and goes straight to one made`() {
        connection.send { it.calls += "play" }
        connection.sendIfConnected { it.calls += "while connecting" }
        val remote = Remote()
        future.set(remote)

        connection.sendIfConnected { it.calls += "connected" }

        remote.calls shouldBe listOf("play", "while connecting", "connected")
    }

    @Test
    fun `with a single connection given, once it fails commands are dropped without trying it again`() {
        val failed = SettableFuture.create<Remote>()
        var listeners = 0
        val counted = object : ForwardingListenableFuture.SimpleForwardingListenableFuture<Remote>(failed) {
            override fun addListener(listener: Runnable, executor: Executor) {
                listeners++
                super.addListener(listener, executor)
            }
        }
        val single = DeferredConnection(counted, MoreExecutors.directExecutor()) { connectedTo += it }
        failed.setException(IOException("service gone"))

        single.send { it.calls += "dropped" }
        single.send { it.calls += "dropped too" }

        listeners shouldBe 1
        connectedTo.shouldBeEmpty()
    }
}
