package org.jellyfin.mobile.downloads

import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jellyfin.mobile.data.entity.DownloadEntity
import org.jellyfin.mobile.player.source.LocalJellyfinMediaSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/** The downloads' work, run in the view model so it outlives the screen that asked. */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {

    private val downloaded = mutableListOf<List<String>>()
    private val removed = mutableListOf<String>()
    private var repairs = 0
    private val repairing = CompletableDeferred<Unit>()
    private var repairFails = false

    private val viewModel by lazy {
        DownloadsViewModel(
            finished = emptyFlow(),
            downloadTracks = { downloaded += it },
            removeTrack = { removed += it },
            repairDownloads = {
                repairs++
                if (repairFails) error("Server unreachable")
                repairing.await()
            },
        )
    }

    @BeforeEach
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun `removing a download removes its track, not its media source`() {
        val trackId = UUID.randomUUID()
        val mediaSource = mockk<LocalJellyfinMediaSource>()
        every { mediaSource.itemId } returns trackId
        every { mediaSource.id } returns trackId.toString().replace("-", "")
        every { mediaSource.downloadSize } returns 0L

        viewModel.remove(DownloadEntity(mediaSource))

        removed shouldBe listOf(trackId.toString())
    }

    @Test
    fun `a book's download and removal go track by track`() {
        viewModel.download(listOf("a", "b"))
        viewModel.remove(listOf("a", "b"))

        downloaded shouldBe listOf(listOf("a", "b"))
        removed shouldBe listOf("a", "b")
    }

    @Test
    fun `repairing runs once, however often it's asked`() {
        viewModel.repairOnce()
        viewModel.repairOnce()
        repairing.complete(Unit)
        viewModel.repairOnce()

        repairs shouldBe 1
    }

    @Test
    fun `a repair that fails runs again when next asked`() {
        repairFails = true
        viewModel.repairOnce()
        repairFails = false

        viewModel.repairOnce()

        repairs shouldBe 2
    }
}
