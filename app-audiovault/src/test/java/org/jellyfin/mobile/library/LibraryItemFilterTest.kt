package org.jellyfin.mobile.library

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.jupiter.api.Test
import java.util.UUID

/** Basic functionality coverage for [filterLibraryItems] — the search-query, downloaded-only, and hidden-item filters. */
class LibraryItemFilterTest {

    private fun filterState(
        downloadedOnly: Boolean = false,
        downloadedIds: Set<String> = emptySet(),
        showHidden: Boolean = false,
        hiddenIds: Set<String> = emptySet(),
    ) = LibraryFilterState(downloadedOnly, downloadedIds, showHidden, hiddenIds)

    private fun bundle(name: String, trackIds: List<UUID> = listOf(UUID.randomUUID())) = LibraryItem.AudiobookBundle(
        id = UUID.randomUUID(),
        displayName = name,
        description = "",
        imageItemId = null,
        imageTag = null,
        runTimeTicks = null,
        progressPercent = null,
        trackIds = trackIds,
        isBundle = true,
        parentFolderId = null,
    )

    @Test
    fun `blank query passes all items through`() {
        val items = listOf(bundle("A"), bundle("B"))
        filterLibraryItems(items, query = "", filter = filterState()) shouldContainExactly items
    }

    @Test
    fun `query matches on displayName, case-insensitively`() {
        val items = listOf(bundle("The Great Gatsby"), bundle("Moby Dick"))
        val result = filterLibraryItems(items, query = "great", filter = filterState())
        result.map { it.displayName } shouldBe listOf("The Great Gatsby")
    }

    @Test
    fun `downloadedOnly off keeps items regardless of download status`() {
        val items = listOf(bundle("A"))
        filterLibraryItems(items, query = "", filter = filterState()) shouldContainExactly items
    }

    @Test
    fun `bundle with any downloaded track passes the downloadedOnly filter`() {
        val downloaded = UUID.randomUUID()
        val notDownloaded = UUID.randomUUID()
        val item = bundle("Book", trackIds = listOf(downloaded, notDownloaded))
        val result = filterLibraryItems(
            listOf(item),
            query = "",
            filter = filterState(downloadedOnly = true, downloadedIds = setOf(downloaded.toString())),
        )
        result shouldContainExactly listOf(item)
    }

    @Test
    fun `bundle with no downloaded tracks is excluded when downloadedOnly is set`() {
        val item = bundle("Book", trackIds = listOf(UUID.randomUUID()))
        filterLibraryItems(listOf(item), query = "", filter = filterState(downloadedOnly = true)) shouldBe emptyList()
    }

    @Test
    fun `AudioFile is excluded when its own id is not in the downloaded set`() {
        val item = LibraryItem.AudioFile(
            BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.AUDIO, name = "Track"),
        )
        filterLibraryItems(listOf(item), query = "", filter = filterState(downloadedOnly = true)) shouldBe emptyList()
    }

    @Test
    fun `Album and BrowseFolder always pass the downloadedOnly filter`() {
        val album = LibraryItem.Album(
            BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.MUSIC_ALBUM, name = "Album"),
        )
        val folder = LibraryItem.BrowseFolder(
            BaseItemDto(id = UUID.randomUUID(), type = BaseItemKind.FOLDER, name = "Folder"),
        )
        val result = filterLibraryItems(
            listOf(album, folder),
            query = "",
            filter = filterState(downloadedOnly = true),
        )
        result shouldContainExactly listOf(album, folder)
    }

    @Test
    fun `hidden item is excluded by default`() {
        val hidden = bundle("Hidden")
        val visible = bundle("Visible")
        val result = filterLibraryItems(
            listOf(hidden, visible),
            query = "",
            filter = filterState(hiddenIds = setOf(hidden.id.toString())),
        )
        result shouldContainExactly listOf(visible)
    }

    @Test
    fun `hidden item is shown when showHidden is set`() {
        val hidden = bundle("Hidden")
        val visible = bundle("Visible")
        val result = filterLibraryItems(
            listOf(hidden, visible),
            query = "",
            filter = filterState(showHidden = true, hiddenIds = setOf(hidden.id.toString())),
        )
        result shouldContainExactly listOf(hidden, visible)
    }

    @Test
    fun `hidden and downloadedOnly filters compose`() {
        val downloadedButHidden = bundle("A", trackIds = listOf(UUID.randomUUID()))
        val result = filterLibraryItems(
            listOf(downloadedButHidden),
            query = "",
            filter = filterState(
                downloadedOnly = true,
                downloadedIds = downloadedButHidden.trackIds.map { it.toString() }.toSet(),
                hiddenIds = setOf(downloadedButHidden.id.toString()),
            ),
        )
        result shouldBe emptyList()
    }
}
