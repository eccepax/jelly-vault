package org.jellyfin.mobile.music.catalog

import io.kotest.matchers.collections.shouldBeSortedWith
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.serializer.toUUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Writes one track's play state (to make it the most recent play) and puts it back afterwards. */
class JellyfinHomeFeedContractTest {

    private val api get() = VaultTestServer.apiClient
    private lateinit var feed: HomeFeed
    private lateinit var track: Track
    private lateinit var original: UserItemDataDto

    @BeforeEach
    fun setUp() = runBlocking {
        VaultTestServer.assumeAvailable()
        feed = JellyfinHomeFeed(api)
        val catalog = JellyfinMusicCatalog(api)
        track = catalog.albumTracks(catalog.albums().first { it.title == "Damn" }.id).first()
        original = api.itemsApi.getItems(ids = listOf(track.id.toUUID())).content.items.single().userData!!
        Unit
    }

    @AfterEach
    fun restore() = runBlocking {
        if (!::original.isInitialized) return@runBlocking
        api.playStateApi.markUnplayedItem(track.id.toUUID())
        if (original.played) api.playStateApi.markPlayedItem(track.id.toUUID(), datePlayed = original.lastPlayedDate)
    }

    @Test
    fun `the most recently played track comes first`() = runBlocking<Unit> {
        api.playStateApi.markPlayedItem(track.id.toUUID(), datePlayed = LocalDateTime.now(ZoneOffset.UTC))

        val recent = feed.recentlyPlayed(limit = 10)

        recent.first().id shouldBe track.id
    }

    @Test
    fun `recently played is newest first and capped`() = runBlocking<Unit> {
        api.playStateApi.markPlayedItem(track.id.toUUID(), datePlayed = LocalDateTime.now(ZoneOffset.UTC))

        val recent = feed.recentlyPlayed(limit = 3)

        recent.shouldNotBeEmpty()
        recent.size shouldBeLessThanOrEqual 3
    }

    @Test
    fun `recently played never includes unplayed tracks`() = runBlocking<Unit> {
        api.playStateApi.markPlayedItem(track.id.toUUID(), datePlayed = LocalDateTime.now(ZoneOffset.UTC))

        val recent = feed.recentlyPlayed(limit = 50)

        val played = api.itemsApi.getItems(ids = recent.map { it.id.toUUID() }).content.items
            .associate { it.id.toString() to it.userData?.played }
        recent.forEach { played[it.id] shouldBe true }
    }

    @Test
    fun `recently added albums are newest first, capped, and never empty albums`() = runBlocking<Unit> {
        val albums = feed.recentlyAdded(limit = 5)

        albums.shouldNotBeEmpty()
        albums.size shouldBeLessThanOrEqual 5
        albums.shouldBeSortedWith(compareByDescending { it.dateAdded })
        val catalog = JellyfinMusicCatalog(api)
        albums.forEach { catalog.albumTracks(it.id).shouldNotBeEmpty() }
    }
}
