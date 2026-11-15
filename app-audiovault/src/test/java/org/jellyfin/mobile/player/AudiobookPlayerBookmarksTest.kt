package org.jellyfin.mobile.player

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.data.entity.AudiobookBookmarkEntity
import org.jellyfin.mobile.player.playback.AudiobookTrack
import org.jellyfin.mobile.player.playback.Chapter
import org.jellyfin.mobile.player.playback.FakeAudiobookController
import org.jellyfin.mobile.robolectric.playerViewModel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** Bookmarks in the player: saving the place playing, going back to one, and changing or deleting one. */
@OptIn(ExperimentalCoroutinesApi::class)
class AudiobookPlayerBookmarksTest {

    private val controller = FakeAudiobookController()
    private val bookmarks = FakeBookmarks()
    private val part1 = AudiobookTrack(
        id = "part1",
        title = "Part 1",
        durationMs = 600_000L,
        chapters = listOf(Chapter(0L, "One"), Chapter(60_000L, "Two")),
    )
    private val part2 = AudiobookTrack(id = "part2", title = "Part 2", durationMs = 600_000L)

    @BeforeEach
    fun setMain() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun resetMain() = Dispatchers.resetMain()

    private fun viewModel() = playerViewModel(controller, bookmarks = bookmarks, now = { 42L })

    @Test
    fun `a bookmark saves the place playing, named after its chapter and time, with its note`() = runTest {
        val viewModel = viewModel()
        controller.playing(part1, part2, positionMs = 75_000L)

        viewModel.addBookmark("the twist")

        bookmarks.saved.value shouldBe listOf(
            AudiobookBookmarkEntity(id = 1L, trackId = "part1", positionMs = 75_000L, label = "Two · 1:15", note = "the twist", createdAt = 42L),
        )
    }

    @Test
    fun `a part with no chapters is named after the part, and a blank note is no note`() = runTest {
        val viewModel = viewModel()
        controller.playing(part1, part2, index = 1, positionMs = 5_000L)

        viewModel.addBookmark("  ")

        bookmarks.saved.value.single().let { (it.label to it.note) shouldBe ("Part 2 · 0:05" to null) }
    }

    @Test
    fun `the book's bookmarks are those in its parts, the newest first`() = runTest {
        val viewModel = viewModel()
        controller.playing(part1, part2)
        bookmarks.saved.value = listOf(
            AudiobookBookmarkEntity(1L, "part1", 1_000L, "older", null, createdAt = 1L),
            AudiobookBookmarkEntity(2L, "another book", 1_000L, "elsewhere", null, createdAt = 2L),
            AudiobookBookmarkEntity(3L, "part2", 1_000L, "newer", null, createdAt = 3L),
        )

        viewModel.bookmarks.value.map { it.label } shouldBe listOf("newer", "older")
    }

    @Test
    fun `going to a bookmark in another part moves to that part, then to the place`() = runTest {
        val viewModel = viewModel()
        controller.playing(part1, part2)
        bookmarks.saved.value = listOf(AudiobookBookmarkEntity(7L, "part2", 90_000L, "Part 2 · 1:30", null, 1L))

        viewModel.openBookmark(7L)

        controller.commands shouldBe listOf("skipTo 1", "seekTo 90000")
    }

    @Test
    fun `going to a bookmark in the part playing just moves to the place`() = runTest {
        val viewModel = viewModel()
        controller.playing(part1, part2, positionMs = 300_000L)
        bookmarks.saved.value = listOf(AudiobookBookmarkEntity(7L, "part1", 90_000L, "Two · 1:30", null, 1L))

        viewModel.openBookmark(7L)

        controller.commands shouldBe listOf("seekTo 90000")
    }

    @Test
    fun `a bookmark's note can be changed, and the bookmark deleted`() = runTest {
        val viewModel = viewModel()
        controller.playing(part1)
        bookmarks.saved.value = listOf(AudiobookBookmarkEntity(7L, "part1", 90_000L, "Two · 1:30", "twist", 1L))

        viewModel.editBookmarkNote(7L, "big twist")
        bookmarks.saved.value.single().note shouldBe "big twist"

        viewModel.deleteBookmark(7L)
        bookmarks.saved.value shouldBe emptyList()
    }
}
