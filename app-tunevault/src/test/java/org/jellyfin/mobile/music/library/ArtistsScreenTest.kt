package org.jellyfin.mobile.music.library

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.domain.Artist
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class ArtistsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var opened: Artist? = null
    private var retried = 0

    private fun show(state: ArtistsUiState) {
        composeRule.setContent {
            ArtistsScreen(state = state, onRetry = { retried++ }, onArtistClick = { opened = it })
        }
    }

    @Test
    fun `lists artists`() {
        show(ArtistsUiState.Loaded(listOf(Artist("1", "Daft Punk"), Artist("2", "Tool"))))

        composeRule.onNodeWithText("Daft Punk").assertIsDisplayed()
        composeRule.onNodeWithText("Tool").assertIsDisplayed()
    }

    @Test
    fun `tapping an artist opens them`() {
        show(ArtistsUiState.Loaded(listOf(Artist("1", "Daft Punk"))))

        composeRule.onNodeWithText("Daft Punk").performClick()

        opened shouldBe Artist("1", "Daft Punk")
    }

    @Test
    fun `loading, empty and error states`() {
        show(ArtistsUiState.Loading)
        composeRule.onNodeWithTag(ArtistsScreenTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun `no artists says so`() {
        show(ArtistsUiState.Loaded(emptyList()))

        composeRule.onNodeWithText("No artists found").assertIsDisplayed()
    }

    @Test
    fun `an error offers a retry`() {
        show(ArtistsUiState.Error)

        composeRule.onNodeWithText("Couldn't load artists").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()

        retried shouldBe 1
    }
}
