package org.jellyfin.mobile.music.catalog

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jellyfin.mobile.music.domain.Track
import org.jellyfin.mobile.testing.VaultTestServer
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Creates clearly named playlists on the real Vault and deletes every one of them afterwards, even
 * when a test fails.
 */
class JellyfinPlaylistStoreContractTest {

    private lateinit var store: PlaylistStore
    private lateinit var tracks: List<Track>
    private val created = mutableListOf<String>()

    @BeforeEach
    fun setUp() = runBlocking {
        VaultTestServer.assumeAvailable()
        store = JellyfinPlaylistStore(VaultTestServer.apiClient)
        val catalog = JellyfinMusicCatalog(VaultTestServer.apiClient)
        tracks = catalog.albumTracks(catalog.albums().first { it.title == "Discovery" }.id).take(4)
    }

    // Not runCatching: a delete the server refuses must fail the run, or every run leaves
    // playlists behind on the Vault (hundreds did, while the test account couldn't delete).
    // The wait: Jellyfin writes a playlist's file in the background after a change, and a delete
    // within about a second of one has the folder written back after it's gone, which the next
    // library scan brings back as an empty playlist nobody owns.
    @AfterEach
    fun deleteTestPlaylists() = runBlocking<Unit> {
        if (created.isNotEmpty()) delay(SERVER_SAVE_SETTLE_MS)
        created.forEach { store.deletePlaylist(it) }
    }

    private suspend fun newPlaylist(vararg tracks: Track): String =
        store.createPlaylist(
            "TuneVault test playlist ${UUID.randomUUID()}",
            tracks.map { it.id },
        ).also { created += it }

    @Test
    fun `a created playlist is listed with its name and size`() = runBlocking<Unit> {
        val id = newPlaylist(tracks[0], tracks[1])

        val playlist = store.playlists().single { it.id == id }

        playlist.name.startsWith("TuneVault test playlist") shouldBe true
        playlist.trackCount shouldBe 2
    }

    @Test
    fun `a playlist plays its songs in order`() = runBlocking<Unit> {
        val id = newPlaylist(tracks[2], tracks[0], tracks[1])

        store.playlistEntries(id).map { it.track.id } shouldBe listOf(tracks[2].id, tracks[0].id, tracks[1].id)
    }

    @Test
    fun `songs can be added to the end`() = runBlocking<Unit> {
        val id = newPlaylist(tracks[0])

        store.addToPlaylist(id, listOf(tracks[1].id, tracks[2].id))

        store.playlistEntries(id).map { it.track.id } shouldBe listOf(tracks[0].id, tracks[1].id, tracks[2].id)
    }

    @Test
    fun `removing an entry removes only that one`() = runBlocking<Unit> {
        val id = newPlaylist(tracks[0], tracks[1], tracks[2])
        val middle = store.playlistEntries(id)[1]

        store.removeFromPlaylist(id, middle.entryId)

        store.playlistEntries(id).map { it.track.id } shouldBe listOf(tracks[0].id, tracks[2].id)
    }

    @Test
    fun `an empty playlist can be created and filled`() = runBlocking<Unit> {
        val id = newPlaylist()

        store.playlistEntries(id) shouldBe emptyList()
        store.addToPlaylist(id, listOf(tracks[3].id))
        store.playlistEntries(id).map { it.track.id } shouldBe listOf(tracks[3].id)
    }

    @Test
    fun `a deleted playlist is gone`() = runBlocking<Unit> {
        val id = newPlaylist(tracks[0])
        delay(SERVER_SAVE_SETTLE_MS) // see deleteTestPlaylists

        store.deletePlaylist(id)
        created -= id

        store.playlists().map { it.id } shouldNotContain id
    }

    @Test
    fun `playlists that came as m3u files inside album folders are not listed`() = runBlocking<Unit> {
        // Many album downloads ship an .m3u of their own tracks, which Jellyfin imports as a playlist
        // that only duplicates the album. Prove some exist here, so this guard isn't vacuous.
        val imported = VaultTestServer.apiClient.itemsApi.getItems(
            includeItemTypes = listOf(BaseItemKind.PLAYLIST),
            recursive = true,
            fields = listOf(ItemFields.PATH),
        ).content.items.filter { it.path.orEmpty().endsWith(".m3u", ignoreCase = true) }.map { it.id.toString() }
        imported.shouldNotBeEmpty()

        store.playlists().map { it.id }.filter { it in imported }.shouldBeEmpty()
    }

    @Test
    fun `only music playlists are listed`() = runBlocking<Unit> {
        val id = newPlaylist(tracks[0])

        store.playlists().map { it.id } shouldContain id
        store.playlists().forEach { playlist ->
            store.playlistEntries(playlist.id).forEach {
                it.track.title.isNotBlank() shouldBe true
            }
        }
    }
}

private const val SERVER_SAVE_SETTLE_MS = 2_000L
