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

    private val future = SettableFuture.create<Remote>()
    private val connectedTo = mutableListOf<Remote>()
    private val connection = DeferredConnection(future, MoreExecutors.directExecutor()) { connectedTo += it }

    @Test
    fun `commands sent before the connection are replayed in order once it's up`() {
        connection.send { it.calls += "first" }
        connection.send { it.calls += "second" }
        val remote = Remote()

        future.set(remote)

        remote.calls shouldBe listOf("first", "second")
    }

    @Test
    fun `commands after the connection go straight through`() {
        val remote = Remote()
        future.set(remote)

        connection.send { it.calls += "now" }

        remote.calls shouldBe listOf("now")
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
    fun `when the connection fails, commands are dropped rather than kept forever`() {
        connection.send { it.calls += "lost" }

        future.setException(IOException("service gone"))
        connection.send { it.calls += "also lost" }

        connectedTo.shouldBeEmpty()
        connection.connected shouldBe null
    }
}
