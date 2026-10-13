package org.jellyfin.mobile.music.downloads

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.music.domain.track
import org.jellyfin.mobile.music.playback.FakePlaybackController
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadedViewModelTest {

    private val downloads = FakeCollectionDownloads()
    private val playback = FakePlaybackController()
    private val album = DownloadedCollection(
        "pb",
        DownloadedCollection.Kind.ALBUM,
        "The Purple Bird",
        "Bonnie",
        null,
        listOf(track("a"), track("b")),
    )
    private val playlist = DownloadedCollection(
        "road",
        DownloadedCollection.Kind.PLAYLIST,
        "Road trip",
        null,
        null,
        listOf(track("x")),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `lists downloaded albums and playlists with how far each has got`() {
        downloads.collections.value = listOf(playlist, album)
        downloads.statuses.value = mapOf("pb" to DownloadStatus.Downloaded, "road" to DownloadStatus.InProgress(0, 1))

        val items = DownloadedViewModel(downloads, playback).items.value

        items shouldBe listOf(
            DownloadedItem(playlist, DownloadStatus.InProgress(0, 1)),
            DownloadedItem(album, DownloadStatus.Downloaded),
        )
    }

    @Test
    fun `nothing downloaded is an empty list`() {
        DownloadedViewModel(downloads, playback).items.value shouldBe emptyList()
    }

    @Test
    fun `shuffle plays every downloaded song once`() {
        downloads.collections.value = listOf(playlist, album.copy(tracks = album.tracks + track("x")))
        downloads.downloadedTrackIds.value = setOf("a", "b", "x")

        DownloadedViewModel(downloads, playback).shuffleAll()

        playback.commands shouldBe listOf("playShuffled x,a,b")
    }

    @Test
    fun `shuffle skips songs still downloading`() {
        downloads.collections.value = listOf(album)
        downloads.downloadedTrackIds.value = setOf("b")

        DownloadedViewModel(downloads, playback).shuffleAll()

        playback.commands shouldBe listOf("playShuffled b")
    }
}
