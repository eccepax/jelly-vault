package org.jellyfin.mobile.stats

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.data.entity.FinishedBookEntity
import org.jellyfin.mobile.player.playback.AudiobookTrack
import org.jellyfin.mobile.player.playback.FakeAudiobookController
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** [ListeningLog]: the time a book actually plays, by day, and the books played to their end. */
@OptIn(ExperimentalCoroutinesApi::class)
class ListeningLogTest {

    private val controller = FakeAudiobookController()
    private val store = FakeListeningStore()
    private var today = LocalDate.of(2026, 11, 15)
    private val part1 = AudiobookTrack(id = "p1", title = "Part 1", bookTitle = "Dune", durationMs = 600_000L)
    private val part2 = AudiobookTrack(id = "p2", title = "Part 2", bookTitle = "Dune", durationMs = 600_000L)

    private fun TestScope.log() = ListeningLog(controller, store, today = { today }, now = { 99L }).also {
        it.start(backgroundScope)
        runCurrent()
    }

    private fun TestScope.wait(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun `time playing is kept for the day, saved every half minute`() = runTest {
        log()
        controller.playing(part1)

        wait(ListeningLog.SAVE_EVERY_MS + 1)

        store.days shouldBe mapOf(today to ListeningLog.SAVE_EVERY_MS)
    }

    @Test
    fun `pausing saves what was listened since, and time paused isn't counted`() = runTest {
        log()
        controller.playing(part1)
        wait(10_000L)

        controller.pause()
        wait(60_000L)

        store.days shouldBe mapOf(today to 10_000L)
    }

    @Test
    fun `listening past midnight counts for the new day`() = runTest {
        log()
        controller.playing(part1)
        wait(10_000L)

        today = today.plusDays(1)
        wait(5_000L)
        controller.pause()
        runCurrent()

        store.days shouldBe mapOf(today.minusDays(1) to 10_000L, today to 5_000L)
    }

    @Test
    fun `a book played to the end of its last part is finished, once`() = runTest {
        log()
        controller.playing(part1, part2, index = 1)

        controller.state.value = controller.state.value.copy(isEnded = true, isPlaying = false)
        runCurrent()
        controller.state.value = controller.state.value.copy(playWhenReady = false)
        runCurrent()

        store.finished shouldBe listOf(FinishedBookEntity(bookKey = "p1", title = "Dune", finishedAt = 99L))
    }

    @Test
    fun `a part ending with more to come isn't the book finished`() = runTest {
        log()
        controller.playing(part1, part2, index = 0)

        controller.state.value = controller.state.value.copy(isEnded = true, isPlaying = false)
        runCurrent()

        store.finished shouldBe emptyList()
    }

    private class FakeListeningStore : ListeningStore {
        val days = mutableMapOf<LocalDate, Long>()
        val finished = mutableListOf<FinishedBookEntity>()

        override suspend fun addListened(day: LocalDate, ms: Long) {
            days[day] = (days[day] ?: 0L) + ms
        }

        override suspend fun bookFinished(book: FinishedBookEntity) {
            finished += book
        }
    }
}
