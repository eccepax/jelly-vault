package org.jellyfin.mobile.downloads

import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.common.server.JellyfinServerPositions
import org.jellyfin.mobile.testing.VaultFixtures.Audiobooks
import org.jellyfin.mobile.testing.VaultTestServer
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UpdateUserItemDataDto
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.extensions.inWholeTicks
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import java.time.Duration as JavaDuration

/**
 * Listening sent to the test server later, as of when it happened. Writes a track's play data, so
 * all of it is put back exactly after each test.
 */
class JellyfinServerPositionsContractTest {

    private val apiClient get() = VaultTestServer.apiClient
    private val positions get() = JellyfinServerPositions(apiClient)
    private lateinit var track: UUID
    private lateinit var original: UserItemDataDto

    @BeforeEach
    fun setUp() = runBlocking {
        VaultTestServer.assumeAvailable()
        track = apiClient.itemsApi.getItems(
            includeItemTypes = listOf(BaseItemKind.AUDIO_BOOK),
            recursive = true,
            searchTerm = Audiobooks.MP3_SEARCH_TERM,
        ).content.items.orEmpty().single().id!!
        original = userData()
    }

    /** The update call can't clear a field (null means "leave it"), so wipe the play data first. */
    @AfterEach
    fun restore() = runBlocking<Unit> {
        if (!::original.isInitialized) return@runBlocking
        apiClient.playStateApi.markUnplayedItem(track)
        apiClient.itemsApi.updateItemUserData(
            itemId = track,
            data = UpdateUserItemDataDto(
                playbackPositionTicks = original.playbackPositionTicks,
                playCount = original.playCount,
                played = original.played,
                lastPlayedDate = original.lastPlayedDate,
            ),
        )
    }

    private suspend fun userData(): UserItemDataDto = apiClient.itemsApi.getItemUserData(track).content

    @Test
    fun `a position is recorded as of when it was listened to`() = runBlocking<Unit> {
        val listenedAt = Instant.now().minus(JavaDuration.ofHours(2))

        positions.record(track, 7.minutes, finished = false, at = listenedAt)

        userData().playbackPositionTicks shouldBe 7.minutes.inWholeTicks
        val lastPlayed = positions.lastPlayed(track).shouldNotBeNull()
        JavaDuration.between(listenedAt, lastPlayed).abs().toMillis() shouldBeLessThan 60_000L
    }

    @Test
    fun `a finished track is recorded as played, from the start`() = runBlocking<Unit> {
        positions.record(track, 7.minutes, finished = true, at = Instant.now())

        val data = userData()
        data.played shouldBe true
        data.playbackPositionTicks shouldBe 0L
    }

    @Test
    fun `restoring puts the play data back exactly`() = runBlocking<Unit> {
        positions.record(track, 7.minutes, finished = true, at = Instant.now())

        restore()

        val restored = userData()
        restored.playbackPositionTicks shouldBe original.playbackPositionTicks
        restored.played shouldBe original.played
        restored.lastPlayedDate shouldBe original.lastPlayedDate
    }
}
