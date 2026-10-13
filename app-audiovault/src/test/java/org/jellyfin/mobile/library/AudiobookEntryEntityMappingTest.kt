package org.jellyfin.mobile.library

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.data.entity.AudiobookEntryEntity
import org.junit.jupiter.api.Test
import java.util.UUID

/** Tests mapping cached [AudiobookEntryEntity] rows to [LibraryItem]s. */
class AudiobookEntryEntityMappingTest {

    @Suppress("LongParameterList")
    private fun entity(
        id: String = "B:dir:album",
        parentId: String? = null,
        title: String = "Title",
        author: String? = null,
        trackIds: String = UUID.randomUUID().toString(),
        isBundle: Boolean = true,
        runtimeTicks: Long = 100L,
        dateAdded: String = "",
        lastPlayed: String = "",
        playedPercentage: Float = 0f,
    ): AudiobookEntryEntity = AudiobookEntryEntity(
        id = id,
        serverId = 1L,
        parentId = parentId,
        title = title,
        author = author,
        imageItemId = null,
        imageTag = null,
        runtimeTicks = runtimeTicks,
        trackIds = trackIds,
        isBundle = isBundle,
        dateAdded = dateAdded,
        lastPlayed = lastPlayed,
        playedPercentage = playedPercentage,
    )

    // --- toLibraryItems sorting ----------------------------------------------------------------

    @Test
    fun `TITLE sort is case-insensitive`() {
        val a = entity(title = "banana")
        val b = entity(title = "Apple")
        listOf(a, b).toLibraryItems(SortOption.TITLE).map { it.displayName } shouldContainExactly listOf("Apple", "banana")
    }

    @Test
    fun `AUTHOR sort treats a null author as an empty string, sorting it first`() {
        val noAuthor = entity(title = "No Author", author = null)
        val withAuthor = entity(title = "Has Author", author = "Zed")
        listOf(withAuthor, noAuthor).toLibraryItems(SortOption.AUTHOR).map { it.displayName } shouldContainExactly
            listOf("No Author", "Has Author")
    }

    @Test
    fun `DATE_ADDED sorts descending`() {
        val early = entity(title = "Early", dateAdded = "2020-01-01")
        val late = entity(title = "Late", dateAdded = "2021-01-01")
        listOf(early, late).toLibraryItems(SortOption.DATE_ADDED).map {
            it.displayName
        } shouldContainExactly listOf("Late", "Early")
    }

    @Test
    fun `LAST_PLAYED sorts descending`() {
        val early = entity(title = "Early", lastPlayed = "2020-01-01")
        val late = entity(title = "Late", lastPlayed = "2021-01-01")
        listOf(early, late).toLibraryItems(SortOption.LAST_PLAYED).map {
            it.displayName
        } shouldContainExactly listOf("Late", "Early")
    }

    // --- toLibraryItem mapping ------------------------------------------------------------------

    @Test
    fun `trackIds CSV parses into a list of UUIDs`() {
        val id1 = UUID.randomUUID()
        val id2 = UUID.randomUUID()
        val item = entity(trackIds = "$id1,$id2").toLibraryItem()
        item.trackIds shouldBe listOf(id1, id2)
    }

    @Test
    fun `a malformed UUID segment is silently dropped, valid segments kept`() {
        val id1 = UUID.randomUUID()
        val item = entity(trackIds = "$id1,not-a-uuid").toLibraryItem()
        item.trackIds shouldBe listOf(id1)
    }

    @Test
    fun `empty trackIds produces an empty list`() {
        entity(trackIds = "").toLibraryItem().trackIds shouldBe emptyList()
    }

    @Test
    fun `trackIds with surrounding whitespace and blank segments are handled`() {
        val id1 = UUID.randomUUID()
        val item = entity(trackIds = " $id1 , ,").toLibraryItem()
        item.trackIds shouldBe listOf(id1)
    }

    @Test
    fun `bundle stable id is a deterministic hash of the entity id`() {
        val entity = entity(id = "B:dir:album", isBundle = true)
        entity.toLibraryItem().id shouldBe UUID.nameUUIDFromBytes(entity.id.toByteArray())
    }

    @Test
    fun `standalone stable id is the first track's own UUID`() {
        val trackId = UUID.randomUUID()
        val item = entity(trackIds = trackId.toString(), isBundle = false).toLibraryItem()
        item.id shouldBe trackId
    }

    @Test
    fun `standalone with unparseable trackIds falls back to a hash of the entity id`() {
        val entity = entity(id = "S:weird", trackIds = "not-a-uuid", isBundle = false)
        entity.toLibraryItem().id shouldBe UUID.nameUUIDFromBytes(entity.id.toByteArray())
    }

    @Test
    fun `malformed parentId string maps to a null parentFolderId instead of throwing`() {
        entity(parentId = "not-a-uuid").toLibraryItem().parentFolderId shouldBe null
    }

    @Test
    fun `zero or negative runtime and percentage map to null instead of zero`() {
        val item = entity(runtimeTicks = 0L, playedPercentage = 0f).toLibraryItem()
        item.runTimeTicks shouldBe null
        item.progressPercent shouldBe null
    }

    // --- SortOption.fromName ---------------------------------------------------------------------

    @Test
    fun `SortOption fromName resolves a valid name`() {
        SortOption.fromName("AUTHOR") shouldBe SortOption.AUTHOR
    }

    @Test
    fun `SortOption fromName defaults to TITLE for an unknown or null name`() {
        SortOption.fromName("NOT_A_REAL_OPTION") shouldBe SortOption.TITLE
        SortOption.fromName(null) shouldBe SortOption.TITLE
    }
}
