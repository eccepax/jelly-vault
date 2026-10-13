package org.jellyfin.mobile.music.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PlayQueueEditingTest {

    @Test
    fun `play next inserts right after the current track`() {
        val queue = PlayQueue.of(tracks("a", "b", "c")).skipToNext().playNext(tracks("x", "y"))

        queue.ids shouldBe listOf("a", "b", "x", "y", "c")
        queue.currentId shouldBe "b"
    }

    @Test
    fun `add to queue appends at the end`() {
        val queue = PlayQueue.of(tracks("a", "b")).addToQueue(tracks("x"))

        queue.ids shouldBe listOf("a", "b", "x")
        queue.currentId shouldBe "a"
    }

    @Test
    fun `adding to an empty queue makes the first added track current`() {
        PlayQueue.EMPTY.addToQueue(tracks("x", "y")).currentId shouldBe "x"
        PlayQueue.EMPTY.playNext(tracks("x", "y")).ids shouldBe listOf("x", "y")
        PlayQueue.EMPTY.playNext(tracks("x", "y")).currentId shouldBe "x"
    }

    @Test
    fun `removing a track before the current keeps the same track current`() {
        val queue = PlayQueue.of(tracks("a", "b", "c"), startIndex = 2).remove(0)

        queue.ids shouldBe listOf("b", "c")
        queue.currentId shouldBe "c"
    }

    @Test
    fun `removing a track after the current keeps the same track current`() {
        val queue = PlayQueue.of(tracks("a", "b", "c")).remove(2)

        queue.ids shouldBe listOf("a", "b")
        queue.currentId shouldBe "a"
    }

    @Test
    fun `removing the current track makes the following track current`() {
        PlayQueue.of(tracks("a", "b", "c"), startIndex = 1).remove(1).currentId shouldBe "c"
    }

    @Test
    fun `removing the current last track falls back to the new last track`() {
        PlayQueue.of(tracks("a", "b", "c"), startIndex = 2).remove(2).currentId shouldBe "b"
    }

    @Test
    fun `removing the only track empties the queue`() {
        val queue = PlayQueue.of(tracks("a")).remove(0)

        queue.tracks shouldBe emptyList()
        queue.current shouldBe null
    }

    @Test
    fun `removing an out-of-range index does nothing`() {
        val queue = PlayQueue.of(tracks("a", "b"))

        queue.remove(5) shouldBe queue
    }

    @Test
    fun `moving the current track keeps it current`() {
        val queue = PlayQueue.of(tracks("a", "b", "c")).move(from = 0, to = 2)

        queue.ids shouldBe listOf("b", "c", "a")
        queue.currentId shouldBe "a"
    }

    @Test
    fun `moving a track across the current one keeps the same track current`() {
        val forward = PlayQueue.of(tracks("a", "b", "c"), startIndex = 1).move(from = 0, to = 2)
        forward.ids shouldBe listOf("b", "c", "a")
        forward.currentId shouldBe "b"

        val backward = PlayQueue.of(tracks("a", "b", "c"), startIndex = 1).move(from = 2, to = 0)
        backward.ids shouldBe listOf("c", "a", "b")
        backward.currentId shouldBe "b"
    }

    @Test
    fun `moving with an out-of-range index does nothing`() {
        val queue = PlayQueue.of(tracks("a", "b"))

        queue.move(from = 0, to = 5) shouldBe queue
        queue.move(from = -1, to = 0) shouldBe queue
    }

    @Test
    fun `the same track can be queued twice and each copy is removed independently`() {
        val queue = PlayQueue.of(tracks("a", "b")).addToQueue(tracks("a"))

        queue.ids shouldBe listOf("a", "b", "a")
        queue.remove(2).ids shouldBe listOf("a", "b")
        queue.remove(2).currentIndex shouldBe 0
    }
}
