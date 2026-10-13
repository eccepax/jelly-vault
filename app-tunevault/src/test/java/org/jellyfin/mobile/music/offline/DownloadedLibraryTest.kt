package org.jellyfin.mobile.music.offline

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.music.domain.track
import org.jellyfin.mobile.music.downloads.DownloadedCollection
import org.jellyfin.mobile.music.downloads.DownloadedCollection.Kind
import org.junit.jupiter.api.Test

/** What the app can show and play with no connection: only songs fully on the device. */
class DownloadedLibraryTest {

    private val a = track(
        "a",
    ).copy(title = "Around the World", artist = "Daft Punk", albumId = "hw", albumTitle = "Homework")
    private val b = track("b").copy(title = "Da Funk", artist = "Daft Punk", albumId = "hw", albumTitle = "Homework")
    private val x = track(
        "x",
    ).copy(title = "London May", artist = "Bonnie", albumId = "pb", albumTitle = "The Purple Bird")
    private val collections = MutableStateFlow(
        listOf(
            DownloadedCollection("road", Kind.PLAYLIST, "Road trip", null, null, listOf(x, a)),
            DownloadedCollection("hw", Kind.ALBUM, "Homework", "Daft Punk", "cover://hw", listOf(a, b)),
        ),
    )
    private val downloaded = MutableStateFlow(setOf("a", "b", "x"))
    private val library = DownloadedLibrary(collections, downloaded)

    @Test
    fun `albums are the downloaded albums`() = runTest {
        library.albums().map { it.title } shouldBe listOf("Homework")
        library.album("hw")?.coverUrl shouldBe "cover://hw"
        library.album("pb") shouldBe null
    }

    @Test
    fun `an album lists only its songs on the device`() = runTest {
        downloaded.value = setOf("a")

        library.albumTracks("hw").map { it.id } shouldBe listOf("a")
    }

    @Test
    fun `songs are every downloaded song once`() = runTest {
        library.tracks().map { it.id }.sorted() shouldBe listOf("a", "b", "x")
    }

    @Test
    fun `playlists are the downloaded playlists, songs in order`() = runTest {
        library.playlists().single().name shouldBe "Road trip"
        library.playlists().single().trackCount shouldBe 2
        library.playlistEntries("road").map { it.track.id } shouldBe listOf("x", "a")
    }

    @Test
    fun `search matches downloaded songs and albums by title or artist`() = runTest {
        val results = library.search("daft")

        results.tracks.map { it.id } shouldBe listOf("a", "b")
        results.albums.map { it.id } shouldBe listOf("hw")
        results.artists.shouldBeEmpty()
        library.search("london").tracks.map { it.id } shouldBe listOf("x")
    }

    @Test
    fun `home offers the downloaded albums, and nothing as recently played`() = runTest {
        library.recentlyAdded(limit = 10).map { it.id } shouldBe listOf("hw")
        library.recentlyPlayed(limit = 10).shouldBeEmpty()
    }

    @Test
    fun `artists aren't browsable offline`() = runTest {
        library.albumArtists().shouldBeEmpty()
        library.artist("daft") shouldBe null
    }
}
