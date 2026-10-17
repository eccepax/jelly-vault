package org.jellyfin.mobile.music.catalog

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldBeSortedWith
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jellyfin.mobile.testing.VaultTestServer
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant

/**
 * Runs [JellyfinMusicCatalog] against the real Vault, so these assert properties that hold for any
 * music library, plus one known album as a sanity anchor.
 */
class JellyfinMusicCatalogContractTest {

    private lateinit var catalog: MusicCatalog

    @BeforeEach
    fun setUp() {
        VaultTestServer.assumeAvailable()
        catalog = JellyfinMusicCatalog(VaultTestServer.apiClient)
    }

    @Test
    fun `albums come from the music library`() = runBlocking<Unit> {
        val albums = catalog.albums()

        albums.shouldNotBeEmpty()
        albums.map { it.title } shouldContain "The Purple Bird"
        albums.forEach { it.title.shouldNotBeBlank() }
    }

    @Test
    fun `each album is listed once even when music libraries overlap`() = runBlocking<Unit> {
        // Two libraries over the same folder (or nested folders) return the same items from each.
        // Duplicate ids also crash the album grid, whose items are keyed by id.
        catalog.albums().map { it.id }.shouldBeUnique()
    }

    @Test
    fun `each artist album is listed once`() = runBlocking<Unit> {
        catalog.albumArtists().forEach { artist ->
            catalog.artistAlbums(artist.id).map { it.id }.shouldBeUnique()
        }
    }

    @Test
    fun `album cover urls serve images`() = runBlocking<Unit> {
        val covers = catalog.albums().mapNotNull { it.coverUrl }

        covers.shouldNotBeEmpty()
        covers.forEach { url ->
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.responseCode shouldBe 200
            connection.contentType shouldStartWith "image/"
        }
    }

    @Test
    fun `album by id matches the album list`() = runBlocking<Unit> {
        catalog.albums().forEach { album ->
            catalog.album(album.id) shouldBe album
        }
    }

    @Test
    fun `album by unknown id is null`() = runBlocking<Unit> {
        catalog.album("00000000000000000000000000000001") shouldBe null
    }

    @Test
    fun `every album's stream urls serve audio`() = runBlocking<Unit> {
        // One track per album: albums share a format, so this covers every format in the Vault
        // (FLAC incl. 24-bit/96kHz, MP3, ...) without opening hundreds of streams each run.
        catalog.albums().forEach { album ->
            catalog.albumTracks(album.id).take(1).forEach { track ->
                val connection = URI(catalog.streamUrl(track.id)).toURL().openConnection() as HttpURLConnection
                connection.setRequestProperty("Range", "bytes=0-1023")
                try {
                    connection.responseCode shouldBeIn listOf(200, 206)
                    connection.contentType shouldStartWith "audio/"
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    @Test
    fun `tracks carry their album's cover`() = runBlocking<Unit> {
        catalog.albums().filter { it.coverUrl != null }.forEach { album ->
            catalog.albumTracks(album.id).take(1).forEach { track ->
                val connection = URI(track.coverUrl!!).toURL().openConnection() as HttpURLConnection
                connection.responseCode shouldBe 200
                connection.contentType shouldStartWith "image/"
            }
        }
    }

    @Test
    fun `albums carry when they were added to the server`() = runBlocking<Unit> {
        val now = Instant.now()
        catalog.albums().forEach { album ->
            album.dateAdded.shouldNotBeNull()
            album.dateAdded!!.isAfter(now) shouldBe false
        }
    }

    @Test
    fun `when an album was added is the time the server stores`() = runBlocking<Unit> {
        // Compared with the raw JSON: the SDK's own decoding of the date is what's under test.
        val album = catalog.albums().first { it.title == "The Purple Bird" }
        val raw = VaultTestServer.rawJson("/Items?ids=${album.id}&fields=DateCreated")["Items"]!!
            .jsonArray.single().jsonObject["DateCreated"]!!.jsonPrimitive.content

        album.dateAdded shouldBe Instant.parse(raw)
    }

    @Test
    fun `artist by id matches the artist list`() = runBlocking<Unit> {
        catalog.albumArtists().forEach { artist ->
            catalog.artist(artist.id) shouldBe artist
        }
    }

    @Test
    fun `a track's artist page lists the track's album`() = runBlocking<Unit> {
        val album = catalog.albums().first { it.artist.isNotBlank() }
        val track = catalog.albumTracks(album.id).first()

        val artistId = track.artistId.shouldNotBeNull()
        catalog.artistAlbums(artistId).map { it.id } shouldContain album.id
    }

    @Test
    fun `artist by unknown id is null`() = runBlocking<Unit> {
        catalog.artist("00000000000000000000000000000001") shouldBe null
    }

    @Test
    fun `all tracks are the music libraries' tracks, never audiobooks`() = runBlocking<Unit> {
        val tracks = catalog.tracks()
        val audiobookTrackIds = VaultTestServer.apiClient.userViewsApi.getUserViews().content.items
            .filter { it.collectionType == CollectionType.BOOKS }
            .flatMap { library ->
                VaultTestServer.apiClient.itemsApi.getItems(
                    parentId = library.id,
                    includeItemTypes = listOf(BaseItemKind.AUDIO, BaseItemKind.AUDIO_BOOK),
                    recursive = true,
                ).content.items
            }
            .map { it.id.toString() }
            .toSet()

        // This server files audiobooks as AudioBook items, which an Audio query already skips; the
        // music-library scoping guards against libraries that store them as plain Audio. Either way
        // the guard must have something to check against.
        audiobookTrackIds.shouldNotBeEmpty()
        tracks.shouldNotBeEmpty()
        tracks.map { it.id }.shouldBeUnique()
        tracks.filter { it.id in audiobookTrackIds }.shouldBeEmpty()
        // A known song, as a sanity anchor.
        tracks.map { it.title } shouldContain "London May"
    }

    @Test
    fun `albums are sorted by title`() = runBlocking<Unit> {
        catalog.albums().shouldBeSortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    }

    @Test
    fun `album artists are only musicians, not audiobook authors`() = runBlocking<Unit> {
        // Audiobooks are Audio items too, so an unscoped album-artist query also returns authors
        // (Howard Zinn, Stephen Fry, ...) who have no music albums at all.
        val artists = catalog.albumArtists()

        artists.shouldNotBeEmpty()
        artists.forEach { artist ->
            catalog.artistAlbums(artist.id).shouldNotBeEmpty()
        }
    }

    @Test
    fun `artist pages list no empty albums`() = runBlocking<Unit> {
        // A folder left with only .cue/.log/cover files stays an album on the server, with no tracks.
        catalog.albumArtists().forEach { artist ->
            catalog.artistAlbums(artist.id).forEach { album -> catalog.albumTracks(album.id).shouldNotBeEmpty() }
        }
    }

    @Test
    fun `artist albums all belong to that artist`() = runBlocking<Unit> {
        catalog.albumArtists().forEach { artist ->
            catalog.artistAlbums(artist.id).forEach { album -> album.artist shouldBe artist.name }
        }
    }

    @Test
    fun `album tracks belong to the album and play in disc then track order`() = runBlocking<Unit> {
        catalog.albums().forEach { album ->
            val tracks = catalog.albumTracks(album.id)

            tracks.shouldNotBeEmpty()
            tracks.forEach { track ->
                track.albumId shouldBe album.id
                track.title.shouldNotBeBlank()
                // 0 = unknown: the server hasn't (or couldn't) read the file's length, e.g. a file
                // scanned while still being copied. The player learns the real length when it plays.
                track.durationMs shouldBeGreaterThanOrEqual 0L
            }
            tracks.shouldBeSortedWith(compareBy({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }))
        }
    }

    @Test
    fun `songs carry the server's loudness gain where it has measured one`() = runBlocking<Unit> {
        val gains = catalog.tracks().mapNotNull { it.normalizationGainDb }

        gains.shouldNotBeEmpty()
        gains.forEach { (it in -30f..30f) shouldBe true }
    }
}
