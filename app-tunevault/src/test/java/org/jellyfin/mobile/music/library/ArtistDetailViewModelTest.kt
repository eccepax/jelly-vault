package org.jellyfin.mobile.music.library

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.music.catalog.FakeMusicCatalog
import org.jellyfin.mobile.music.catalog.album
import org.jellyfin.mobile.music.domain.Artist
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistDetailViewModelTest {

    private val kendrick = Artist("k", "Kendrick Lamar")
    private val catalog = FakeMusicCatalog(
        artists = listOf(kendrick),
        albums = listOf(
            album("s80", "Section.80", artist = "Kendrick Lamar", year = 2011),
            album("damn", "Damn", artist = "Kendrick Lamar", year = 2017),
            album("hw", "Homework", artist = "Daft Punk", year = 1997),
        ),
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
    fun `shows the artist's albums, newest first`() {
        val state = ArtistDetailViewModel("k", catalog).state.value as ArtistDetailUiState.Loaded

        state.artist shouldBe kendrick
        state.albums.map { it.id } shouldBe listOf("damn", "s80")
    }

    @Test
    fun `an unknown artist is not found`() {
        ArtistDetailViewModel("nope", catalog).state.value shouldBe ArtistDetailUiState.NotFound
    }

    @Test
    fun `a failed load shows an error and retry recovers`() {
        catalog.failure = IOException("offline")
        val viewModel = ArtistDetailViewModel("k", catalog)
        viewModel.state.value shouldBe ArtistDetailUiState.Error

        catalog.failure = null
        viewModel.retry()

        (viewModel.state.value as ArtistDetailUiState.Loaded).artist shouldBe kendrick
    }
}
