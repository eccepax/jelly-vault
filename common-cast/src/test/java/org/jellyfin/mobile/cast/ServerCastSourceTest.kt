package org.jellyfin.mobile.cast

import android.app.Application
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** A song's cast source from the server (real time: what its file is may take a while to hear). */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
internal class ServerCastSourceTest {

    private val server = MockWebServer().apply { start() }

    private val api = createJellyfin {
        context = RuntimeEnvironment.getApplication()
        clientInfo = ClientInfo("Test", "1.0")
        deviceInfo = DeviceInfo("test-device", "Test device")
    }.createApi(baseUrl = server.url("/").toString().trimEnd('/'), accessToken = "token")

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `cast as MP3, the server isn't asked what the file is`() = runBlocking<Unit> {
        val source = serverCastSource(api, ITEM_ID, keepFormat = { false })

        server.requestCount shouldBe 0
        source.streamFrom(0L).mimeType shouldBe "audio/mpeg"
    }

    @Test
    fun `kept in its own format, the server is asked what the file is`() = runBlocking<Unit> {
        server.enqueue(MockResponse().setResponseCode(500))

        val source = serverCastSource(api, ITEM_ID, keepFormat = { true })

        server.requestCount shouldBe 1
        // It couldn't say: cast as MP3
        source.streamFrom(0L).mimeType shouldBe "audio/mpeg"
    }

    @Test
    fun `an id that isn't the server's is turned down when it's found, not when it's cast`() = runBlocking<Unit> {
        shouldThrow<IllegalArgumentException> { serverCastSource(api, "not-an-id", keepFormat = { false }) }
    }

    private companion object {
        const val ITEM_ID = "0123456789abcdef0123456789abcdef"
    }
}
