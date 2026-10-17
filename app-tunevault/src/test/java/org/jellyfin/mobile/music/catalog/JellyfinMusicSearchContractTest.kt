package org.jellyfin.mobile.music.catalog

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.testing.VaultFixtures.Audiobooks
import org.jellyfin.mobile.testing.VaultFixtures.PatternSuite
import org.jellyfin.mobile.testing.VaultFixtures.SignalPath
import org.jellyfin.mobile.testing.VaultTestServer
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JellyfinMusicSearchContractTest {

    private lateinit var search: MusicSearch

    @BeforeEach
    fun setUp() {
        VaultTestServer.assumeAvailable()
        search = JellyfinMusicSearch(VaultTestServer.apiClient)
    }

    @Test
    fun `finds an album by part of its title, ignoring case`() = runBlocking<Unit> {
        search.search(PatternSuite.SEARCH_TERM).albums.map { it.title } shouldContain PatternSuite.TITLE
    }

    @Test
    fun `finds an artist by part of their name`() = runBlocking<Unit> {
        search.search(SignalPath.ARTIST_SEARCH_TERM).artists.map { it.name } shouldContain SignalPath.ARTIST
    }

    @Test
    fun `finds a song by its title`() = runBlocking<Unit> {
        search.search(PatternSuite.KNOWN_SONG).tracks.map { it.title } shouldContain PatternSuite.KNOWN_SONG
    }

    @Test
    fun `results are capped per section`() = runBlocking<Unit> {
        val results = search.search("a")

        results.albums.size shouldBeLessThanOrEqual JellyfinMusicSearch.MAX_PER_SECTION
        results.tracks.size shouldBeLessThanOrEqual JellyfinMusicSearch.MAX_PER_SECTION
        results.artists.size shouldBeLessThanOrEqual JellyfinMusicSearch.MAX_PER_SECTION
    }

    @Test
    fun `nothing matching finds nothing`() = runBlocking<Unit> {
        search.search("zzqqxx nothing like this") shouldBe SearchResults.EMPTY
    }

    @Test
    fun `never returns audiobooks`() = runBlocking<Unit> {
        val term = Audiobooks.SEARCH_TERM_MATCHING_AN_AUDIOBOOK
        val api = VaultTestServer.apiClient
        // Prove the term really matches audiobooks on this server, so this guard isn't vacuous.
        val audiobookMatches = api.userViewsApi.getUserViews().content.items
            .filter { it.collectionType == CollectionType.BOOKS }
            .flatMap { api.itemsApi.getItems(parentId = it.id, searchTerm = term, recursive = true).content.items }
        audiobookMatches.shouldNotBeEmpty()
        val audiobookIds = audiobookMatches.map { it.id.toString() }.toSet()

        val results = search.search(term)

        (results.albums.map { it.id } + results.tracks.map { it.id } + results.artists.map { it.id })
            .filter { it in audiobookIds }
            .shouldBeEmpty()
    }
}
