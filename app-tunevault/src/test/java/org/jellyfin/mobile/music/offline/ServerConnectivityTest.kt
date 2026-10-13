package org.jellyfin.mobile.music.offline

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.junit.jupiter.api.Test
import java.io.IOException
import java.net.ConnectException

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConnectivityTest {

    private var serverUp = true
    private var serverCalls = 0

    private fun TestScope.connectivity() = ServerConnectivity(ping = { serverUp }, scope = backgroundScope)

    private suspend fun ServerConnectivity.fetch(failure: Exception? = null): String = choose(
        online = {
            serverCalls++
            failure?.let { throw it } ?: "server"
        },
        offline = { "downloads" },
    )

    @Test
    fun `while the server answers, answers come from it`() = runTest {
        val connectivity = connectivity()

        connectivity.fetch() shouldBe "server"
        connectivity.isOffline.value shouldBe false
    }

    @Test
    fun `when the network fails, answers come from downloads and the app is offline`() = runTest {
        val connectivity = connectivity()

        connectivity.fetch(ConnectException("refused")) shouldBe "downloads"
        connectivity.isOffline.value shouldBe true
    }

    @Test
    fun `the sdk's own network failures count too`() = runTest {
        val connectivity = connectivity()

        connectivity.fetch(ApiClientException("timeout", IOException("timed out"))) shouldBe "downloads"
    }

    @Test
    fun `a server error is not being offline`() = runTest {
        val connectivity = connectivity()

        shouldThrow<InvalidStatusException> { connectivity.fetch(InvalidStatusException(500)) }
        connectivity.isOffline.value shouldBe false
    }

    @Test
    fun `cancellation is passed on, not taken for offline`() = runTest {
        val connectivity = connectivity()

        shouldThrow<CancellationException> { connectivity.fetch(CancellationException("left the screen")) }
        connectivity.isOffline.value shouldBe false
    }

    @Test
    fun `once offline, the server isn't tried for every screen`() = runTest {
        val connectivity = connectivity()
        connectivity.fetch(ConnectException("refused"))

        connectivity.fetch() shouldBe "downloads"
        connectivity.fetch() shouldBe "downloads"

        serverCalls shouldBe 1
    }

    @Test
    fun `back online once the server answers a check`() = runTest {
        val connectivity = connectivity()
        serverUp = false
        connectivity.fetch(ConnectException("refused"))

        advanceTimeBy(ServerConnectivity.RETRY_INTERVAL_MS + 1)
        runCurrent()
        connectivity.isOffline.value shouldBe true

        serverUp = true
        advanceTimeBy(ServerConnectivity.RETRY_INTERVAL_MS + 1)
        runCurrent()
        connectivity.isOffline.value shouldBe false
        connectivity.fetch() shouldBe "server"
    }

    @Test
    fun `a check that throws keeps it offline`() = runTest {
        val connectivity = ServerConnectivity(ping = { throw IOException("still down") }, scope = backgroundScope)
        connectivity.fetch(ConnectException("refused"))

        advanceTimeBy(ServerConnectivity.RETRY_INTERVAL_MS * 3)
        runCurrent()

        connectivity.isOffline.value shouldBe true
    }
}
