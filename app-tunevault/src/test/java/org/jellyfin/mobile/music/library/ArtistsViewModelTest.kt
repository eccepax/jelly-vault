package org.jellyfin.mobile.music.library

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.music.catalog.FakeMusicCatalog
import org.jellyfin.mobile.music.domain.Artist
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistsViewModelTest {

    private val catalog = FakeMusicCatalog()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `shows loading until the catalog answers`() {
        catalog.gate = CompletableDeferred()

        ArtistsViewModel(catalog).state.value shouldBe ArtistsUiState.Loading
    }

    @Test
    fun `lists artists sorted ignoring articles and punctuation`() {
        catalog.artists = listOf(Artist("1", "Tool"), Artist("2", "The Beatles"), Artist("3", "\"Weird Al\" Yankovic"))

        val state = ArtistsViewModel(catalog).state.value as ArtistsUiState.Loaded

        state.artists.map { it.name } shouldBe listOf("The Beatles", "Tool", "\"Weird Al\" Yankovic")
    }

    @Test
    fun `a failed load shows an error and retry recovers`() {
        catalog.failure = IOException("offline")
        val viewModel = ArtistsViewModel(catalog)
        viewModel.state.value shouldBe ArtistsUiState.Error

        catalog.failure = null
        catalog.artists = listOf(Artist("1", "Tool"))
        viewModel.retry()

        viewModel.state.value shouldBe ArtistsUiState.Loaded(listOf(Artist("1", "Tool")))
    }
}
