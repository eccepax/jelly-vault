package org.jellyfin.mobile.music.home

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.music.catalog.FakeFavoritesStore
import org.jellyfin.mobile.music.catalog.FakeHomeFeed
import org.jellyfin.mobile.music.catalog.FakeMusicCatalog
import org.jellyfin.mobile.music.catalog.album
import org.jellyfin.mobile.music.domain.track
import org.jellyfin.mobile.music.playback.FakePlaybackController
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private fun played(id: String, albumId: String, albumTitle: String) =
        track(
            id,
        ).copy(albumId = albumId, albumTitle = albumTitle, artist = "Artist $albumId", coverUrl = "cover/$albumId")

    private val feed = FakeHomeFeed(
        played = listOf(
            played("1", "damn", "Damn"),
            played("2", "pb", "The Purple Bird"),
            played("3", "damn", "Damn"),
        ),
        added = listOf(album("new", "Newest"), album("older", "Older")),
    )
    private val catalog = FakeMusicCatalog(tracksByAlbum = mapOf("x" to listOf(track("a"), track("b"))))
    private val playback = FakePlaybackController()
    private val favorites = FakeFavoritesStore(
        tracks = listOf(track("f1"), track("f2")),
        albums = listOf(album("fa", "Favorite Album")),
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun loaded(viewModel: HomeViewModel) = viewModel.state.value as HomeUiState.Loaded

    @Test
    fun `recently played shows each album once, by most recent play`() {
        val shelf = loaded(HomeViewModel(feed, catalog, playback, favorites)).recentlyPlayed

        shelf.map { it.id } shouldBe listOf("damn", "pb")
        shelf.first().title shouldBe "Damn"
        shelf.first().coverUrl shouldBe "cover/damn"
    }

    @Test
    fun `tracks that belong to no album are left off the shelf`() {
        feed.played = listOf(played("1", "", ""), played("2", "pb", "The Purple Bird"))

        loaded(HomeViewModel(feed, catalog, playback, favorites)).recentlyPlayed.map { it.id } shouldBe listOf("pb")
    }

    @Test
    fun `recently added shows the newest albums`() {
        loaded(HomeViewModel(feed, catalog, playback, favorites)).recentlyAdded.map { it.id } shouldBe listOf("new", "older")
    }

    @Test
    fun `nothing played yet is an empty shelf, not an error`() {
        feed.played = emptyList()

        loaded(HomeViewModel(feed, catalog, playback, favorites)).recentlyPlayed shouldBe emptyList()
    }

    @Test
    fun `a failed load shows an error and retry recovers`() {
        feed.failure = IOException("offline")
        val viewModel = HomeViewModel(feed, catalog, playback, favorites)
        viewModel.state.value shouldBe HomeUiState.Error

        feed.failure = null
        viewModel.refresh()

        loaded(viewModel).recentlyPlayed.map { it.id } shouldBe listOf("damn", "pb")
    }

    @Test
    fun `refreshing keeps showing the old shelves until the new ones arrive`() {
        val viewModel = HomeViewModel(feed, catalog, playback, favorites)
        feed.gate = CompletableDeferred()
        feed.played = listOf(played("9", "new", "Newest"))

        viewModel.refresh()
        loaded(viewModel).recentlyPlayed.map { it.id } shouldBe listOf("damn", "pb")

        feed.gate!!.complete(Unit)
        loaded(viewModel).recentlyPlayed.map { it.id } shouldBe listOf("new")
    }

    @Test
    fun `shows favorite albums and songs`() {
        val state = loaded(HomeViewModel(feed, catalog, playback, favorites))

        state.favoriteAlbums.map { it.id } shouldBe listOf("fa")
        state.favoriteTracks.map { it.id } shouldBe listOf("f1", "f2")
    }

    @Test
    fun `tapping a favorite song plays the favorites from it`() {
        HomeViewModel(feed, catalog, playback, favorites).playFavorite(1)

        playback.commands shouldBe listOf("play f1,f2 from 1")
    }

    @Test
    fun `shuffle all shuffles the whole library`() {
        HomeViewModel(feed, catalog, playback, favorites).shuffleAll()

        playback.commands shouldBe listOf("playShuffled a,b")
    }
}
