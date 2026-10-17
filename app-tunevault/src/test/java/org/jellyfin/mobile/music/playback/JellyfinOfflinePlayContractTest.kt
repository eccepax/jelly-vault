package org.jellyfin.mobile.music.playback

import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.mobile.music.catalog.JellyfinMusicCatalog
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.testing.VaultFixtures
import org.jellyfin.mobile.testing.VaultTestServer
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.serializer.toUUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * A play made offline is sent later with the time it happened. Writes the track's play data, so
 * all of it (count, played, last played) is put back exactly after each test.
 */
class JellyfinOfflinePlayContractTest {

    private val apiClient get() = VaultTestServer.apiClient
    private lateinit var track: Track
    private lateinit var original: UserItemDataDto

    @BeforeEach
    fun setUp() = runBlocking {
        VaultTestServer.assumeAvailable()
        val catalog = JellyfinMusicCatalog(apiClient)
        track = catalog.albumTracks(catalog.albums().first { it.title == VaultFixtures.PLAY_STATE_ALBUM }.id).first()
        original = userData()
    }

    /**
     * The update call can't clear a field (null means "leave it"), so first wipe the play data,
     * which also clears the last-played date, then put back only what there was.
     */
    @AfterEach
    fun restore() = runBlocking<Unit> {
        if (!::original.isInitialized) return@runBlocking
        apiClient.playStateApi.markUnplayedItem(track.id.toUUID())
        apiClient.itemsApi.updateItemUserData(
            itemId = track.id.toUUID(),
            data = UpdateUserItemDataDto(
                playCount = original.playCount,
                played = original.played,
                lastPlayedDate = original.lastPlayedDate,
            ),
        )
    }

    private suspend fun userData(): UserItemDataDto = apiClient.itemsApi.getItemUserData(track.id.toUUID()).content

    @Test
    fun `a play made offline counts once, at the time it happened`() = runBlocking<Unit> {
        val before = userData().playCount
        val heardAt = Instant.now().minus(Duration.ofHours(2))

        JellyfinPlaybackReporter(apiClient).playedAt(track.id, heardAt)

        val after = userData()
        after.playCount shouldBe before + 1
        after.played shouldBe true
        after.lastPlayedDate.shouldNotBeNull()
        // From the raw JSON: the SDK's own decoding of the date is what's under test.
        val recorded = Instant.parse(
            VaultTestServer.rawJson("/UserItems/${track.id}/UserData")["LastPlayedDate"]!!.jsonPrimitive.content,
        )
        Duration.between(heardAt, recorded).abs().toMillis() shouldBeLessThan 60_000L
    }

    @Test
    fun `restoring puts the play data back exactly`() = runBlocking<Unit> {
        JellyfinPlaybackReporter(apiClient).playedAt(track.id, Instant.now())

        restore()

        val restored = userData()
        restored.playCount shouldBe original.playCount
        restored.played shouldBe original.played
        restored.lastPlayedDate shouldBe original.lastPlayedDate
    }
}
