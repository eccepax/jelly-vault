package org.jellyfin.mobile.player.session

import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.IOException

class DeferredConnectionTest {

    private class Remote {
        val calls = mutableListOf<String>()
    }

    /** Each connection asked for, in order; the test completes them. */
    private val attempts = mutableListOf<SettableFuture<Remote>>()
    private val future get() = attempts.last()
    private val connectedTo = mutableListOf<Remote>()
    private val connection = DeferredConnection(
        connect = { SettableFuture.create<Remote>().also { attempts += it } },
        callbackExecutor = MoreExecutors.directExecutor(),
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
    fun `a connection lost while it's still being made is let go of`() {
        connection.connect()
        val abandoned = future

        connection.disconnected()
        connection.connect()
        abandoned.set(Remote())

        connectedTo.shouldBeEmpty()
        attempts.size shouldBe 2
    }
}
