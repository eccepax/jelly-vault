package org.jellyfin.mobile.livetv.playback

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldNotBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.mobile.livetv.catalog.JellyfinLiveTvCatalog
import org.jellyfin.mobile.livetv.domain.LiveChannel
import org.jellyfin.mobile.player.deviceprofile.DeviceDecoders
import org.jellyfin.mobile.player.deviceprofile.DeviceProfileBuilder
import org.jellyfin.mobile.player.deviceprofile.castVideoProfile
import org.jellyfin.mobile.testing.VaultFixtures.LiveTv
import org.jellyfin.mobile.testing.VaultTestServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URI

/**
 * Opens real live streams on the server, ONLY on free channels: pointed at a real server, any other
 * channel could take a provider's limited logins. Every stream opened here is closed again.
 */
class JellyfinLiveStreamsContractTest {

    private val api get() = VaultTestServer.apiClient
    private lateinit var streams: JellyfinLiveStreams
    private lateinit var channel: LiveChannel
    private val opened = mutableListOf<OpenedStream>()

    @BeforeEach
    fun setUp() = runBlocking {
        VaultTestServer.assumeAvailable()
        // An emulator-like device: decodes H.264 only, so the server picks what it would for a phone.
        val profile = DeviceProfileBuilder("LiveVault contract tests", includeVideo = true) {
            DeviceDecoders(video = mapOf("h264" to setOf("baseline", "main", "high")), audio = setOf("aac", "mp3"))
        }
        streams = JellyfinLiveStreams(api, profile::getDeviceProfile, castProfile = {
            castVideoProfile("LiveVault contract tests")
        })
        channel = JellyfinLiveTvCatalog(api).channels().filter(::isFree).shouldNotBeEmpty().first()
    }

    @AfterEach
    fun closeEverything() = runBlocking {
        opened.forEach { streams.close(it.ticket) }
    }

    private fun isFree(channel: LiveChannel) = LiveTv.FREE_TAG in channel.tags && channel.name.startsWith(LiveTv.FREE_PREFIX)

    private suspend fun openFree(forCast: Boolean = false): OpenedStream {
        check(isFree(channel)) { "tests may only open free channels" }
        return streams.open(channel.id, forCast).also { opened += it }
    }

    private fun get(url: String): Pair<Int, String> {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.readTimeout = READ_TIMEOUT_MS
        return try {
            connection.responseCode to connection.inputStream.use { it.readNBytes(PEEK_BYTES).decodeToString() }
        } catch (e: java.io.IOException) {
            connection.responseCode to ""
        }
    }

    /** The server's raw stream for [stream]'s live stream: serves bytes only while it's open. */
    private fun rawStreamUrl(stream: OpenedStream) = "${VaultTestServer.hostname}/Videos/${stream.channelId}/stream" +
        "?static=true&mediaSourceId=${stream.mediaSourceId}&liveStreamId=${stream.liveStreamId}&ApiKey=${api.accessToken}"

    @Test
    fun `a free channel opens to a stream the player can fetch`() = runBlocking<Unit> {
        val stream = openFree()

        stream.liveStreamId.shouldNotBeBlank()
        stream.channelId shouldBe channel.id
        val (status, body) = get(stream.url)
        status shouldBe 200
        if (".m3u8" in stream.url) body shouldStartWith "#EXTM3U"
    }

    @Test
    fun `a channel opened for a cast device comes as H264 and AAC over HLS, with the token in the url`() = runBlocking<Unit> {
        // The cast device fetches it itself (no header) and can't play the tuner's raw MPEG-TS.
        val stream = openFree(forCast = true)

        stream.url shouldContain ".m3u8"
        stream.url shouldContain "ApiKey=${api.accessToken}"
        val (status, playlist) = get(stream.url)
        status shouldBe 200
        playlist shouldStartWith "#EXTM3U"
        playlist shouldContain "avc1"
        playlist shouldContain "mp4a"
    }

    @Test
    fun `closing ends the stream on the server`() = runBlocking<Unit> {
        val stream = openFree()
        get(rawStreamUrl(stream)).first shouldBe 200

        streams.close(stream.ticket)

        get(rawStreamUrl(stream)).first shouldNotBeExactly 200
    }

    @Test
    fun `closing one viewer's stream leaves another viewer of the same channel watching`() = runBlocking<Unit> {
        // The server shares one tuner stream between everyone on a channel and counts them: each
        // open adds one, so each close must take away exactly one. Taking two (a stop report AND a
        // close) cut off another device in the house watching the same channel.
        val mine = openFree()
        val theirs = openFree()
        mine.liveStreamId shouldBe theirs.liveStreamId
        // As the app does: the stop report only counts for a stream reported as playing.
        streams.reportPlaying(mine)

        streams.close(mine.ticket)

        get(rawStreamUrl(theirs)).first shouldBe 200
    }

    @Test
    fun `a channel left a second after opening it plays again when you come straight back`() = runBlocking<Unit> {
        // A quick zap: the viewer lands on a channel and leaves while it's still starting up. The
        // tuner proxy used to refuse that channel for 30-45 s afterwards.
        val first = openFree()
        get(rawStreamUrl(first))
        delay(QUICK_ZAP_MS)
        streams.close(first.ticket)

        val again = openFree()

        get(rawStreamUrl(again)).first shouldBe 200
    }

    @Test
    fun `closing an already closed stream is fine`() = runBlocking<Unit> {
        // A ledger entry from a crashed run may already have been closed (or timed out) server-side.
        val stream = openFree()
        streams.close(stream.ticket)

        streams.close(stream.ticket)
    }

    @Test
    fun `while playing, the server shows the channel playing on this device until it closes`() = runBlocking<Unit> {
        val stream = openFree()

        streams.reportPlaying(stream)
        nowPlayingOnThisDevice() shouldBe channel.id.replace("-", "")

        streams.close(stream.ticket)
        nowPlayingOnThisDevice() shouldBe null
    }

    private fun nowPlayingOnThisDevice(): String? {
        val session = VaultTestServer.rawJsonArray("/Sessions?deviceId=${api.deviceInfo.id}").firstOrNull()?.jsonObject
        val item = session?.get("NowPlayingItem") ?: return null
        if (item is JsonNull) return null
        // Raw JSON ids have no dashes.
        return item.jsonObject["Id"]?.jsonPrimitive?.content?.replace("-", "")
    }
}

private const val READ_TIMEOUT_MS = 30_000
private const val PEEK_BYTES = 2_000
private const val QUICK_ZAP_MS = 1_000L
