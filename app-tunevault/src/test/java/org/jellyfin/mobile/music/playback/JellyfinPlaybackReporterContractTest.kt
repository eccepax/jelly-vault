package org.jellyfin.mobile.music.playback

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.music.catalog.JellyfinMusicCatalog
import org.jellyfin.mobile.music.catalog.VaultTestServer
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.serializer.toUUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Reports playback of a real track to the real Vault and checks the server recorded it. Writes the
 * track's play state, so the original state is put back after each test.
 */
class JellyfinPlaybackReporterContractTest {

    private val apiClient get() = VaultTestServer.apiClient
    private lateinit var reporter: PlaybackReporter
    private lateinit var track: Track
    private lateinit var original: UserItemDataDto

    @BeforeEach
    fun setUp() = runBlocking {
        VaultTestServer.assumeAvailable()
        reporter = JellyfinPlaybackReporter(apiClient)
        val catalog = JellyfinMusicCatalog(apiClient)
        val album = catalog.albums().first { it.title == "Wolf of the Cosmos" }
        track = catalog.albumTracks(album.id).first()
        original = userData()
        apiClient.playStateApi.markUnplayedItem(track.id.toUUID())
        Unit
    }

    @AfterEach
    fun restore() = runBlocking {
        if (!::original.isInitialized) return@runBlocking
        apiClient.playStateApi.markUnplayedItem(track.id.toUUID())
        if (original.played) apiClient.playStateApi.markPlayedItem(track.id.toUUID())
        if (original.playbackPositionTicks > 0) {
            reporter.started(track.id, original.playbackPositionTicks / TICKS_PER_MS, isPaused = true)
            reporter.stopped(track.id, original.playbackPositionTicks / TICKS_PER_MS)
        }
    }

    private suspend fun userData(): UserItemDataDto =
        apiClient.itemsApi.getItems(ids = listOf(track.id.toUUID())).content.items.single().userData!!

    @Test
    fun `playing a track to the end marks it played`() = runBlocking<Unit> {
        reporter.started(track.id, positionMs = 0L, isPaused = false)
        reporter.progress(track.id, positionMs = track.durationMs / 2, isPaused = false)
        reporter.stopped(track.id, positionMs = track.durationMs)

        val data = userData()
        data.played shouldBe true
        data.lastPlayedDate shouldNotBe null
    }

    /**
     * Server rule, found by this test: music (unlike audiobooks) doesn't support resume, so Jellyfin
     * counts any reported play as played wherever it stopped, and keeps no resume position.
     */
    @Test
    fun `music stopped part way still counts as played and keeps no resume position`() = runBlocking<Unit> {
        reporter.started(track.id, positionMs = 0L, isPaused = false)
        reporter.stopped(track.id, positionMs = 30_000L)

        val data = userData()
        data.played shouldBe true
        data.playbackPositionTicks shouldBe 0L
    }

    private companion object {
        const val TICKS_PER_MS = 10_000L
    }
}
