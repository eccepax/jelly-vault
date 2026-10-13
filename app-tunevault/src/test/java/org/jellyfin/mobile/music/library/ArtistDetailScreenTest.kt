package org.jellyfin.mobile.music.library

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.catalog.album
import org.jellyfin.mobile.music.domain.Album
import org.jellyfin.mobile.music.domain.Artist
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class ArtistDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val damn = album("damn", "Damn", artist = "Kendrick Lamar", year = 2017)
    private var opened: Album? = null
    private var back = 0

    private fun show(state: ArtistDetailUiState) {
        composeRule.setContent {
            ArtistDetailScreen(state = state, onBack = { back++ }, onRetry = {}, onAlbumClick = { opened = it })
        }
    }

    @Test
    fun `shows the artist and their albums`() {
        show(ArtistDetailUiState.Loaded(Artist("k", "Kendrick Lamar"), listOf(damn)))

        composeRule.onNodeWithText("Kendrick Lamar").assertIsDisplayed()
        composeRule.onNodeWithText("Damn").assertIsDisplayed()
    }

    @Test
    fun `tapping an album opens it`() {
        show(ArtistDetailUiState.Loaded(Artist("k", "Kendrick Lamar"), listOf(damn)))

        composeRule.onNodeWithText("Damn").performClick()

        opened shouldBe damn
    }

    @Test
    fun `back goes back`() {
        show(ArtistDetailUiState.Loaded(Artist("k", "Kendrick Lamar"), listOf(damn)))

        composeRule.onNodeWithContentDescription("Back").performClick()

        back shouldBe 1
    }

    @Test
    fun `an unknown artist says so`() {
        show(ArtistDetailUiState.NotFound)

        composeRule.onNodeWithText("Artist not found").assertIsDisplayed()
    }
}
