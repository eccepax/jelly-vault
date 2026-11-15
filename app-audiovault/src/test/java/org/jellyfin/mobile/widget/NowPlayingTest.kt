package org.jellyfin.mobile.widget

import androidx.datastore.preferences.core.mutablePreferencesOf
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.player.playback.AudiobookTrack
import org.junit.jupiter.api.Test

/** What the widget shows of the book playing, and how it's kept for the widget to read. */
class NowPlayingTest {

    private val chapter3 = AudiobookTrack(id = "t3", title = "Chapter 3", bookTitle = "Dune", durationMs = 1L)

    @Test
    fun `a part of a book shows the book, then the part`() {
        nowPlayingOf(chapter3, isPlaying = true) shouldBe NowPlaying(title = "Dune", detail = "Chapter 3", isPlaying = true)
    }

    @Test
    fun `a book in one file shows just its title`() {
        nowPlayingOf(chapter3.copy(title = "Caste", bookTitle = null), isPlaying = false) shouldBe
            NowPlaying(title = "Caste", detail = null, isPlaying = false)
    }

    @Test
    fun `with nothing loaded there's nothing playing`() {
        nowPlayingOf(null, isPlaying = false) shouldBe null
    }

    @Test
    fun `what's playing is kept for the widget, and so is nothing playing`() {
        val kept = mutablePreferencesOf()
        val dune = NowPlaying(title = "Dune", detail = "Chapter 3", isPlaying = true)

        kept.keep(dune)
        nowPlayingFrom(kept) shouldBe dune

        kept.keep(null)
        nowPlayingFrom(kept) shouldBe null
    }
}
