package org.jellyfin.mobile.library

import android.app.Application
import android.os.Looper
import androidx.recyclerview.widget.RecyclerView
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.jellyfin.sdk.api.client.ApiClient
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Regression coverage for a real user-reported bug: the audiobook list visibly flickers while a
 * download is in progress. Root cause: [LibraryAdapter.updateDownloadedIds] (and
 * [LibraryAdapter.updateHiddenIds]) called `notifyDataSetChanged()` unconditionally on every
 * change — and since a multi-track audiobook finishes downloading one chapter at a time,
 * `LibraryViewModel.downloadedIds` (and therefore this call) fires once per
 * chapter completing, forcing every *visible* row — not just the one that actually changed — to
 * fully rebind (including reloading its cover art via Coil) each time. That repeated whole-list
 * rebind is the visible flicker.
 *
 * The fix notifies only the row(s) whose downloaded/hidden status actually changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class LibraryAdapterDownloadFlickerTest {

    private val apiClient = mockk<ApiClient>(relaxed = true)

    private fun bundle(id: UUID = UUID.randomUUID(), trackIds: List<UUID> = listOf(id)) =
        LibraryItem.AudiobookBundle(
            id = id,
            displayName = "Book",
            description = "",
            imageItemId = null,
            imageTag = null,
            runTimeTicks = null,
            progressPercent = null,
            trackIds = trackIds,
            isBundle = true,
            parentFolderId = null,
        )

    private class RecordingObserver : RecyclerView.AdapterDataObserver() {
        var fullChanges = 0
        val changedPositions = mutableListOf<Int>()

        override fun onChanged() {
            fullChanges++
        }

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) {
            repeat(itemCount) { changedPositions += positionStart + it }
        }
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `a chapter finishing its download only notifies that book's row, not the whole list`() {
        val downloadedTrackId = UUID.randomUUID()
        val downloadingBundle = bundle(trackIds = listOf(downloadedTrackId))
        val unrelatedBundle = bundle()

        val adapter = LibraryAdapter(apiClient, onItemClick = {})
        adapter.submitList(listOf(downloadingBundle, unrelatedBundle))
        idle()

        val observer = RecordingObserver()
        adapter.registerAdapterDataObserver(observer)

        adapter.updateDownloadedIds(setOf(downloadedTrackId.toString()))

        observer.fullChanges shouldBe 0
        observer.changedPositions shouldBe listOf(0)
    }

    @Test
    fun `a second chapter of the same book finishing does not re-notify the whole list either`() {
        val firstTrackId = UUID.randomUUID()
        val secondTrackId = UUID.randomUUID()
        val downloadingBundle = bundle(trackIds = listOf(firstTrackId, secondTrackId))
        val unrelatedBundle = bundle()

        val adapter = LibraryAdapter(apiClient, onItemClick = {})
        adapter.submitList(listOf(downloadingBundle, unrelatedBundle))
        idle()
        adapter.updateDownloadedIds(setOf(firstTrackId.toString()))

        val observer = RecordingObserver()
        adapter.registerAdapterDataObserver(observer)

        // The bundle is already showing as "downloaded" (any track present is enough) — a second
        // chapter of the SAME book completing must not re-notify a row whose visible state doesn't
        // actually change.
        adapter.updateDownloadedIds(setOf(firstTrackId.toString(), secondTrackId.toString()))

        observer.fullChanges shouldBe 0
        observer.changedPositions shouldBe emptyList()
    }

    @Test
    fun `unrelated hidden-id changes elsewhere in the list do not touch this book's row`() {
        val trackId = UUID.randomUUID()
        val thisBundle = bundle(trackIds = listOf(trackId))
        val otherBundle = bundle()

        val adapter = LibraryAdapter(apiClient, onItemClick = {})
        adapter.submitList(listOf(thisBundle, otherBundle))
        idle()

        val observer = RecordingObserver()
        adapter.registerAdapterDataObserver(observer)

        adapter.updateHiddenIds(setOf(otherBundle.id.toString()))

        observer.fullChanges shouldBe 0
        observer.changedPositions shouldBe listOf(1)
    }
}
