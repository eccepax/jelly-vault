package org.jellyfin.mobile.library

import app.cash.turbine.test
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.jellyfin.mobile.data.entity.AudiobookEntryEntity
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.CollectionType
import org.junit.jupiter.api.Test
import java.util.UUID

/** [LibraryViewModel]'s "Continue listening" row and marking books finished. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelContinueListeningTest : LibraryViewModelTestBase() {

    // --- Continue listening ------------------------------------------------------------------------

    private fun entry(title: String, played: Float, lastPlayed: String = "2026-11-01T10:00:00Z") = AudiobookEntryEntity(
        id = "B:$title",
        serverId = 1L,
        title = title,
        author = null,
        imageItemId = null,
        imageTag = null,
        runtimeTicks = 100L,
        playedPercentage = played,
        trackIds = UUID.randomUUID().toString(),
        isBundle = true,
        parentId = null,
        dateAdded = "",
        lastPlayed = lastPlayed,
    )

    /** The whole library from the Room cache, the server never answering (so the cache stays). */
    private fun flatLibraryFromCache(vararg entries: AudiobookEntryEntity) {
        every { appPreferences.libraryGlobalFlattened } returns true
        coEvery { audiobookEntryDao.getAll(1L) } returns entries.toList()
        val never = CompletableDeferred<Unit>()
        coEvery { userViewsApi.getUserViews(userId = any(), includeHidden = any()) } coAnswers {
            never.await()
            queryResult(emptyList())
        }
    }

    private suspend fun LibraryViewModel.continueListeningShouldBecome(expected: List<String>) {
        var names: List<String>? = null
        try {
            continueListening.test {
                names = awaitItem().map { it.displayName }
                while (names != expected) names = awaitItem().map { it.displayName }
                cancelAndIgnoreRemainingEvents()
            }
        } catch (e: AssertionError) {
            names shouldBe expected
            throw e
        }
    }

    @Test
    fun `continue listening holds the books started and not finished`() = runTest {
        flatLibraryFromCache(entry("Started", played = 40f), entry("Not started", played = 0f, lastPlayed = ""))

        val viewModel = createViewModel()

        viewModel.continueListeningShouldBecome(listOf("Started"))
    }

    @Test
    fun `continue listening steps aside while searching or filtering, so the list is just what was asked for`() = runTest {
        flatLibraryFromCache(entry("Started", played = 40f))
        val viewModel = createViewModel()
        viewModel.continueListeningShouldBecome(listOf("Started"))

        viewModel.setFilterQuery("Sta")
        viewModel.continueListeningShouldBecome(emptyList())

        viewModel.setFilterQuery("")
        viewModel.showFinishedOnly.value = true
        viewModel.continueListeningShouldBecome(emptyList())
    }

    @Test
    fun `browsing by folder there's no continue listening`() = runTest {
        every { appPreferences.libraryGlobalFlattened } returns false
        coEvery { audiobookEntryDao.getAll(1L) } returns listOf(entry("Started", played = 40f))

        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.continueListening.value shouldBe emptyList()
    }

    // --- Finished ----------------------------------------------------------------------------------

    @Test
    fun `marking a book finished marks each of its tracks played, then shows the library again`() = runTest {
        val tracks = listOf(UUID.randomUUID(), UUID.randomUUID())
        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.setFinished(tracks, finished = true)
        viewModel.awaitSettled()

        tracks.forEach { coVerify { playStateApi.markPlayedItem(itemId = it, userId = any(), datePlayed = any()) } }
        coVerify(atLeast = 2) { userViewsApi.getUserViews(userId = any(), includeHidden = any()) }
    }

    @Test
    fun `marking a book not finished marks its tracks unplayed, so it starts over`() = runTest {
        val track = UUID.randomUUID()
        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.setFinished(listOf(track), finished = false)
        viewModel.awaitSettled()

        coVerify { playStateApi.markUnplayedItem(itemId = track, userId = any()) }
    }

    @Test
    fun `a book that can't be marked (no connection) says so`() = runTest {
        coEvery { playStateApi.markPlayedItem(itemId = any(), userId = any(), datePlayed = any()) } throws
            TimeoutException("Vault unreachable")
        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.finishFailures.test {
            viewModel.setFinished(listOf(UUID.randomUUID()), finished = true)
            awaitItem()
        }
    }

    // --- Reloading once a book stops -------------------------------------------------------------

    @Test
    fun `once a book stops, the library loads again only after where it got to has been reported`() = runTest {
        val reported = CompletableDeferred<Unit>()
        listeningReported = { reported.await() }
        every { appPreferences.libraryGlobalFlattened } returns true
        coEvery {
            userViewsApi.getUserViews(userId = any(), includeHidden = any())
        } returns queryResult(listOf(libraryView("Books", CollectionType.BOOKS)))
        val viewModel = createViewModel()
        viewModel.awaitSettled()

        viewModel.onPlaybackStopped()
        coVerify(exactly = 1) { userViewsApi.getUserViews(userId = any(), includeHidden = any()) }

        reported.complete(Unit)
        viewModel.awaitSettled()
        coVerify(exactly = 2) { userViewsApi.getUserViews(userId = any(), includeHidden = any()) }
    }
}
