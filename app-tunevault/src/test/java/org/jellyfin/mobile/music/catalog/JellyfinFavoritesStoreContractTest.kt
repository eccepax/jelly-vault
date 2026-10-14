package org.jellyfin.mobile.music.catalog

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.testing.VaultTestServer
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.serializer.toUUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Writes favorite flags on one track and one album of the real Vault, restoring them afterwards. */
class JellyfinFavoritesStoreContractTest {

    private val api get() = VaultTestServer.apiClient
    private lateinit var store: FavoritesStore
    private lateinit var catalog: JellyfinMusicCatalog
    private lateinit var album: Album
    private lateinit var track: Track
    private val originals = mutableMapOf<String, Boolean>()

    @BeforeEach
    fun setUp() = runBlocking {
        VaultTestServer.assumeAvailable()
        store = JellyfinFavoritesStore(api)
        catalog = JellyfinMusicCatalog(api)
        album = catalog.albums().first { it.title == "Homework" }
        track = catalog.albumTracks(album.id).first()
        listOf(album.id, track.id).forEach { originals[it] = isFavoriteOnServer(it) }
        store.setFavorite(album.id, false)
        store.setFavorite(track.id, false)
    }

    @AfterEach
    fun restore() = runBlocking<Unit> {
        originals.forEach { (id, favorite) -> store.setFavorite(id, favorite) }
    }

    private suspend fun isFavoriteOnServer(id: String): Boolean =
        api.itemsApi.getItems(ids = listOf(id.toUUID())).content.items.single().userData?.isFavorite == true

    @Test
    fun `favoriting a song saves it on the server`() = runBlocking<Unit> {
        store.setFavorite(track.id, true)

        isFavoriteOnServer(track.id) shouldBe true
    }

    @Test
    fun `unfavoriting removes it`() = runBlocking<Unit> {
        store.setFavorite(track.id, true)

        store.setFavorite(track.id, false)

        isFavoriteOnServer(track.id) shouldBe false
    }

    @Test
    fun `favorite songs and albums are listed`() = runBlocking<Unit> {
        store.setFavorite(track.id, true)
        store.setFavorite(album.id, true)

        store.favoriteTracks().map { it.id } shouldContain track.id
        store.favoriteAlbums().map { it.id } shouldContain album.id
    }

    @Test
    fun `unfavorited items are not listed`() = runBlocking<Unit> {
        store.favoriteTracks().map { it.id } shouldNotContain track.id
        store.favoriteAlbums().map { it.id } shouldNotContain album.id
    }

    @Test
    fun `the catalog reports favorite flags on tracks and albums`() = runBlocking<Unit> {
        store.setFavorite(track.id, true)
        store.setFavorite(album.id, true)

        catalog.albumTracks(album.id).first { it.id == track.id }.isFavorite shouldBe true
        catalog.album(album.id)?.isFavorite shouldBe true
        catalog.albumTracks(album.id).filter {
            it.id != track.id
        }.forEach { it.isFavorite shouldBe (originals[it.id] ?: false) }
    }

    @Test
    fun `listing favorites never mixes in audiobooks`() = runBlocking<Unit> {
        // Favorites are user-wide; the store must scope them to the music libraries like everything else.
        val musicTrackIds = catalog.tracks().map { it.id }.toSet()

        store.favoriteTracks().forEach { (it.id in musicTrackIds) shouldBe true }
    }
}
