package org.jellyfin.mobile.music.downloads

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.music.domain.track
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h914dp")
class DownloadedScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val album =
        DownloadedCollection(
            "pb",
            DownloadedCollection.Kind.ALBUM,
            "The Purple Bird",
            "Bonnie",
            null,
            listOf(track("a"), track("b")),
        )
    private val playlist =
        DownloadedCollection("road", DownloadedCollection.Kind.PLAYLIST, "Road trip", null, null, listOf(track("x")))
    private val events = mutableListOf<String>()

    private fun show(items: List<DownloadedItem>) {
        composeRule.setContent {
            DownloadedScreen(
                items = items,
                onAlbumClick = { events += "album $it" },
                onPlaylistClick = { events += "playlist $it" },
                onShuffleAll = { events += "shuffle" },
            )
        }
    }

    @Test
    fun `each download says what it is and how far it has got`() {
        show(
            listOf(
                DownloadedItem(album, DownloadStatus.Downloaded),
                DownloadedItem(playlist, DownloadStatus.InProgress(0, 1)),
            ),
        )

        composeRule.onNodeWithText("Album · Bonnie · 2 songs").assertIsDisplayed()
        composeRule.onNodeWithText("Playlist · 1 song").assertIsDisplayed()
        composeRule.onNodeWithText("Downloading 0 of 1").assertIsDisplayed()
    }

    @Test
    fun `tapping opens the album or playlist`() {
        show(
            listOf(
                DownloadedItem(album, DownloadStatus.Downloaded),
                DownloadedItem(playlist, DownloadStatus.Downloaded),
            ),
        )

        composeRule.onNodeWithText("The Purple Bird").performClick()
        composeRule.onNodeWithText("Road trip").performClick()

        events shouldBe listOf("album pb", "playlist road")
    }

    @Test
    fun `shuffle all plays the downloads`() {
        show(listOf(DownloadedItem(album, DownloadStatus.Downloaded)))

        composeRule.onNodeWithText("Shuffle downloads").performClick()

        events shouldBe listOf("shuffle")
    }

    @Test
    fun `with nothing downloaded it explains how`() {
        show(emptyList())

        composeRule.onNodeWithText(
            "Download albums and playlists to play them without a connection.",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Shuffle downloads").assertDoesNotExist()
    }
}
