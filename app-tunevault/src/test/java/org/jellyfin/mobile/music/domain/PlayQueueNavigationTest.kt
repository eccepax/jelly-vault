package org.jellyfin.mobile.music.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PlayQueueNavigationTest {

    @Test
    fun `empty queue has no current track and cannot move`() {
        val queue = PlayQueue.EMPTY

        queue.current shouldBe null
        queue.hasNext shouldBe false
        queue.hasPrevious shouldBe false
        queue.skipToNext() shouldBe queue
        queue.onTrackEnded() shouldBe null
    }

    @Test
    fun `starts at the requested track`() {
        PlayQueue.of(tracks("a", "b", "c"), startIndex = 1).currentId shouldBe "b"
    }

    @Test
    fun `start index is clamped into range`() {
        PlayQueue.of(tracks("a", "b"), startIndex = 9).currentId shouldBe "b"
        PlayQueue.of(tracks("a", "b"), startIndex = -3).currentId shouldBe "a"
    }

    @Test
    fun `skip to next and previous move through the queue`() {
        val queue = PlayQueue.of(tracks("a", "b", "c"))

        queue.skipToNext().currentId shouldBe "b"
        queue.skipToNext().skipToNext().skipToPrevious().currentId shouldBe "b"
    }

    @Test
    fun `with repeat off, skipping stops at both ends`() {
        val atEnd = PlayQueue.of(tracks("a", "b"), startIndex = 1)
        atEnd.hasNext shouldBe false
        atEnd.skipToNext().currentId shouldBe "b"

        val atStart = PlayQueue.of(tracks("a", "b"))
        atStart.hasPrevious shouldBe false
        atStart.skipToPrevious().currentId shouldBe "a"
    }

    @Test
    fun `with repeat all, skipping wraps around at both ends`() {
        val atEnd = PlayQueue.of(tracks("a", "b"), startIndex = 1).withRepeatMode(RepeatMode.ALL)
        atEnd.hasNext shouldBe true
        atEnd.skipToNext().currentId shouldBe "a"

        val atStart = PlayQueue.of(tracks("a", "b")).withRepeatMode(RepeatMode.ALL)
        atStart.hasPrevious shouldBe true
        atStart.skipToPrevious().currentId shouldBe "b"
    }

    @Test
    fun `user skip with repeat one still moves to the next track`() {
        PlayQueue.of(tracks("a", "b")).withRepeatMode(RepeatMode.ONE).skipToNext().currentId shouldBe "b"
    }

    @Test
    fun `track ending advances to the next track`() {
        PlayQueue.of(tracks("a", "b")).onTrackEnded()?.currentId shouldBe "b"
    }

    @Test
    fun `last track ending with repeat off ends playback`() {
        PlayQueue.of(tracks("a", "b"), startIndex = 1).onTrackEnded() shouldBe null
    }

    @Test
    fun `last track ending with repeat all wraps to the first`() {
        PlayQueue.of(tracks("a", "b"), startIndex = 1).withRepeatMode(RepeatMode.ALL)
            .onTrackEnded()?.currentId shouldBe "a"
    }

    @Test
    fun `track ending with repeat one replays the same track`() {
        PlayQueue.of(tracks("a", "b")).withRepeatMode(RepeatMode.ONE).onTrackEnded()?.currentId shouldBe "a"
    }

    @Test
    fun `skip to an index jumps there, ignoring out-of-range indexes`() {
        val queue = PlayQueue.of(tracks("a", "b", "c"))

        queue.skipTo(2).currentId shouldBe "c"
        queue.skipTo(7) shouldBe queue
    }

    @Test
    fun `repeat mode cycles off, all, one, off`() {
        RepeatMode.OFF.next() shouldBe RepeatMode.ALL
        RepeatMode.ALL.next() shouldBe RepeatMode.ONE
        RepeatMode.ONE.next() shouldBe RepeatMode.OFF
    }
}
