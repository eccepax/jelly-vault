package org.jellyfin.mobile.music.domain

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PlayQueueShuffleTest {

    private val tenTracks = tracks("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")

    @Test
    fun `shuffling moves the current track to the front and keeps every track`() {
        val queue = PlayQueue.of(tenTracks, startIndex = 4).shuffled(Random(1))

        queue.isShuffled shouldBe true
        queue.currentIndex shouldBe 0
        queue.currentId shouldBe "4"
        queue.ids shouldContainExactlyInAnyOrder tenTracks.map { it.id }
    }

    @Test
    fun `shuffle order depends only on the random source`() {
        val first = PlayQueue.of(tenTracks).shuffled(Random(42))
        val second = PlayQueue.of(tenTracks).shuffled(Random(42))

        first.ids shouldBe second.ids
        first.ids shouldNotBe tenTracks.map { it.id }
    }

    @Test
    fun `unshuffling restores the original order and keeps the current track`() {
        val shuffled = PlayQueue.of(tenTracks, startIndex = 4).shuffled(Random(7))
        val movedOn = shuffled.skipToNext().skipToNext()
        val nowPlaying = movedOn.currentId!!

        val restored = movedOn.unshuffled()

        restored.isShuffled shouldBe false
        restored.ids shouldBe tenTracks.map { it.id }
        restored.currentId shouldBe nowPlaying
        restored.currentIndex shouldBe nowPlaying.toInt()
    }

    @Test
    fun `unshuffling an unshuffled queue does nothing`() {
        val queue = PlayQueue.of(tenTracks)

        queue.unshuffled() shouldBe queue
    }

    @Test
    fun `shuffling an empty queue does nothing`() {
        PlayQueue.EMPTY.shuffled(Random(1)) shouldBe PlayQueue.EMPTY
    }

    @Test
    fun `tracks added while shuffled land at the end of the original order too`() {
        val queue = PlayQueue.of(tracks("a", "b", "c")).shuffled(Random(3)).addToQueue(tracks("x"))

        queue.ids.last() shouldBe "x"
        queue.unshuffled().ids shouldBe listOf("a", "b", "c", "x")
    }

    @Test
    fun `play next while shuffled sits after the current track in both orders`() {
        val queue = PlayQueue.of(tracks("a", "b", "c", "d"), startIndex = 1).shuffled(Random(3)).playNext(tracks("x"))

        queue.ids[queue.currentIndex + 1] shouldBe "x"
        queue.unshuffled().ids shouldBe listOf("a", "b", "x", "c", "d")
    }

    @Test
    fun `removing while shuffled removes from the original order too`() {
        val shuffled = PlayQueue.of(tracks("a", "b", "c", "d")).shuffled(Random(3))
        val indexOfC = shuffled.ids.indexOf("c")

        shuffled.remove(indexOfC).unshuffled().ids shouldBe listOf("a", "b", "d")
    }

    @Test
    fun `unshuffle keeps the right copy current when a track is queued twice`() {
        // "a" twice; play the second copy, shuffle, unshuffle: the second copy must still be current.
        val queue = PlayQueue.of(tracks("a", "b", "a"), startIndex = 2).shuffled(Random(5)).unshuffled()

        queue.ids shouldBe listOf("a", "b", "a")
        queue.currentIndex shouldBe 2
    }

    @Test
    fun `reshuffling keeps the original order from before the first shuffle`() {
        val queue = PlayQueue.of(tenTracks).shuffled(Random(1)).shuffled(Random(2)).unshuffled()

        queue.ids shouldBe tenTracks.map { it.id }
    }
}
