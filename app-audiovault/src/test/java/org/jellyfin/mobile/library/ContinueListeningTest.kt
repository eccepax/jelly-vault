package org.jellyfin.mobile.library

import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.data.entity.AudiobookEntryEntity
import org.junit.jupiter.api.Test
import java.util.UUID

/** [continueListening]: the books started and not finished, the one listened to last first. */
class ContinueListeningTest {

    private fun book(title: String, played: Float, lastPlayed: String = "2026-11-01T10:00:00Z") = AudiobookEntryEntity(
        id = "B:$title",
        serverId = 1L,
        parentId = null,
        title = title,
        author = null,
        imageItemId = null,
        imageTag = null,
        runtimeTicks = 100L,
        trackIds = UUID.randomUUID().toString(),
        isBundle = true,
        dateAdded = "",
        lastPlayed = lastPlayed,
        playedPercentage = played,
    )

    @Test
    fun `books started and not finished, listened to last first`() {
        val books = listOf(
            book("Older", played = 40f, lastPlayed = "2026-10-01T10:00:00Z"),
            book("Newer", played = 10f, lastPlayed = "2026-11-02T10:00:00Z"),
        )

        continueListening(books).map { it.displayName } shouldBe listOf("Newer", "Older")
    }

    @Test
    fun `books not started, or finished, aren't in it`() {
        val books = listOf(
            book("Not started", played = 0f, lastPlayed = ""),
            book("Finished", played = 100f),
            book("Started", 5f),
        )

        continueListening(books).map { it.displayName } shouldBe listOf("Started")
    }

    @Test
    fun `hidden books aren't in it`() {
        val hidden = book("Hidden", played = 50f)
        val hiddenId = hidden.toLibraryItem().id.toString()

        continueListening(listOf(hidden, book("Shown", 50f)), hiddenIds = setOf(hiddenId)).map { it.displayName } shouldBe
            listOf("Shown")
    }

    @Test
    fun `it holds the ten listened to last at most`() {
        val books = (1..12).map { book("Book $it", played = 50f, lastPlayed = "2026-11-%02dT10:00:00Z".format(it)) }

        continueListening(books).map { it.displayName } shouldBe (12 downTo 3).map { "Book $it" }
    }
}
