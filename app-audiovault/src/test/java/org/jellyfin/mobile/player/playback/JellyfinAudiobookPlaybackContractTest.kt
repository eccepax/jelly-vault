package org.jellyfin.mobile.player.playback

import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.testing.VaultFixtures.Audiobooks
import org.jellyfin.mobile.testing.VaultTestServer
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

/**
 * Playing a book against the test server: its details, the streams the player fetches, and what
 * the server keeps from the listening reported. Puts each track's play data back afterwards.
 */
class JellyfinAudiobookPlaybackContractTest {

    private val api get() = VaultTestServer.apiClient
    private val audiobooks get() = JellyfinAudiobooks(api)
    private val progress get() = JellyfinAudiobookProgress(api)

    private lateinit var m4b: UUID
    private lateinit var original: UserItemDataDto

    @BeforeEach
    fun setUp() = runBlocking {
        VaultTestServer.assumeAvailable()
        m4b = audiobook(Audiobooks.M4B_SEARCH_TERM)
        original = userData(m4b)
    }

    /** The update call can't clear a field (null means "leave it"), so wipe the play data first. */
    @AfterEach
    fun restore() = runBlocking<Unit> {
        if (!::original.isInitialized) return@runBlocking
        api.playStateApi.markUnplayedItem(m4b)
        api.itemsApi.updateItemUserData(
            itemId = m4b,
            data = UpdateUserItemDataDto(
                playbackPositionTicks = original.playbackPositionTicks,
                playCount = original.playCount,
                played = original.played,
                lastPlayedDate = original.lastPlayedDate,
            ),
        )
    }

    @Test
    fun `a book's details come in one request, with its name and length`() = runBlocking<Unit> {
        val item = audiobooks.items(listOf(m4b)).single()

        item.name shouldBe Audiobooks.M4B
        (item.runTimeTicks ?: 0L) shouldBeGreaterThan 0L
    }

    @Test
    fun `asking for a book by name finds it`() = runBlocking<Unit> {
        audiobooks.topMatch(Audiobooks.M4B_SEARCH_TERM) shouldBe m4b
    }

    @Test
    fun `the stream serves the book's audio`() = runBlocking<Unit> {
        val response = get(audiobooks.streamUrl(m4b.toString()))

        response.code shouldBe HttpURLConnection.HTTP_OK
        response.contentType shouldStartWith "audio/"
    }

    @Test
    fun `seeking in a book played as it is fetches a byte range, which the server serves`() = runBlocking<Unit> {
        val response = get(audiobooks.streamUrl(m4b.toString()), range = "bytes=1000-1999")

        response.code shouldBe HttpURLConnection.HTTP_PARTIAL
        response.length shouldBe 1000L
    }

    @Test
    fun `a format the phone can't play is transcoded, and can start part way, as seeking in it does`() = runBlocking<Unit> {
        val transcode = audiobooks.streamUrl(m4b.toString(), directPlayContainers = emptyList())

        val response = get("$transcode&StartTimeTicks=${1.minutes.inWholeMilliseconds * 10_000L}")

        response.code shouldBe HttpURLConnection.HTTP_OK
        response.contentType shouldStartWith "audio/"
    }

    @Test
    fun `an m4b of AAC is cast as it is, served as the type the cast device is told, so it can seek`() = runBlocking<Unit> {
        // The server says what the file is; from part way through, the cast device seeks there in the whole file
        val stream = audiobooks.castSource(m4b.toString()).streamFrom(60_000L)

        // The cast device fetches it on its own: no Authorization header
        val response = get(stream.url, range = "bytes=1000-1999", authorized = false)

        stream.mimeType shouldBe "audio/mp4"
        stream.startMs shouldBe 0L
        stream.seekable shouldBe true
        response.code shouldBe HttpURLConnection.HTTP_PARTIAL
        response.contentType shouldBe stream.mimeType
    }

    @Test
    fun `a book the server can't say anything of is cast as an mp3 transcode, which starts where it's asked for`() =
        runBlocking<Unit> {
            val fromMs = 1.minutes.inWholeMilliseconds
            val stream = audiobooks.castSource(UUID.randomUUID().toString()).streamFrom(fromMs)

            stream.mimeType shouldBe "audio/mpeg"
            stream.startMs shouldBe fromMs
            stream.seekable shouldBe false
            stream.url.lowercase() shouldContain "starttimeticks=${fromMs * 10_000L}"
        }

    @Test
    fun `while a book plays, the server sees where the listener is, and that it's paused`() = runBlocking<Unit> {
        val halfway = runTimeMs() / 2
        val track = AudiobookTrack(id = m4b.toString(), title = "", durationMs = runTimeMs())

        progress.started(track, positionMs = 0L)
        progress.progress(track, positionMs = halfway, isPaused = true)

        val playing = session().playState
        session().nowPlayingItem?.id shouldBe m4b
        playing?.positionTicks shouldBe halfway * 10_000L
        playing?.isPaused shouldBe true
    }

    @Test
    fun `stopping ends the server's play session`() = runBlocking<Unit> {
        val track = AudiobookTrack(id = m4b.toString(), title = "", durationMs = runTimeMs())
        progress.started(track, positionMs = 0L)

        progress.stopped(track, positionMs = 1_000L)

        session().nowPlayingItem shouldBe null
    }

    /**
     * Server rule, found by this test: Jellyfin keeps no resume point in an audiobook's first or last
     * five minutes (its MinAudiobookResume and MaxAudiobookResume), so a short book never has one.
     */
    @Test
    fun `the server keeps no resume point in the first five minutes of a book`() = runBlocking<Unit> {
        val track = AudiobookTrack(id = m4b.toString(), title = "", durationMs = runTimeMs())

        progress.started(track, positionMs = 0L)
        progress.stopped(track, positionMs = runTimeMs() / 2)

        userData(m4b).playbackPositionTicks shouldBe 0L
    }

    @Test
    fun `finishing marks the book played, to start over next time`() = runBlocking<Unit> {
        val runTimeMs = runTimeMs()
        val track = AudiobookTrack(id = m4b.toString(), title = "", durationMs = runTimeMs)

        progress.started(track, positionMs = runTimeMs - 1_000L)
        progress.finished(track)

        val data = userData(m4b)
        data.played shouldBe true
        data.playbackPositionTicks shouldBe 0L
    }

    private suspend fun audiobook(nameContains: String): UUID = api.itemsApi.getItems(
        includeItemTypes = listOf(BaseItemKind.AUDIO_BOOK),
        recursive = true,
        searchTerm = nameContains,
        limit = 1,
    ).content.items.orEmpty().single().id

    /** This client's session on the server. */
    private suspend fun session(): SessionInfoDto =
        api.sessionApi.getSessions(deviceId = api.deviceInfo.id).content.single()

    private suspend fun runTimeMs(): Long = (audiobooks.items(listOf(m4b)).single().runTimeTicks ?: 0L) / 10_000L

    private suspend fun userData(id: UUID): UserItemDataDto = api.itemsApi.getItemUserData(id).content

    private class Response(val code: Int, val contentType: String, val length: Long)

    /**
     * A GET as the player's data source makes it: the token in the Authorization header. [authorized]
     * false makes it as a cast device does, with only the url. Reads no body.
     */
    private fun get(url: String, range: String? = null, authorized: Boolean = true): Response {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        if (authorized) {
            connection.setRequestProperty(
                "Authorization",
                AuthorizationHeaderBuilder.buildHeader(
                    clientName = api.clientInfo.name,
                    clientVersion = api.clientInfo.version,
                    deviceId = api.deviceInfo.id,
                    deviceName = api.deviceInfo.name,
                    accessToken = api.accessToken,
                ),
            )
        }
        range?.let { connection.setRequestProperty("Range", it) }
        return try {
            Response(connection.responseCode, connection.contentType.orEmpty(), connection.contentLengthLong)
        } finally {
            connection.disconnect()
        }
    }
}
