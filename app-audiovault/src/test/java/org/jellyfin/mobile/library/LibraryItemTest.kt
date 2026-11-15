package org.jellyfin.mobile.library

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.junit.jupiter.api.Test
import java.util.UUID

/** Basic functionality coverage for mapping raw Jellyfin items to [LibraryItem] subtypes. */
class LibraryItemTest {

    private fun dto(
        type: BaseItemKind,
        isFolder: Boolean? = null,
        name: String? = "Item Name",
        path: String? = null,
        played: Boolean? = null,
    ): BaseItemDto = BaseItemDto(
        id = UUID.randomUUID(),
        type = type,
        isFolder = isFolder,
        name = name,
        path = path,
        userData = played?.let {
            UserItemDataDto(
                playbackPositionTicks = 0L,
                playCount = 0,
                isFavorite = false,
                played = it,
                key = "",
                itemId = UUID.randomUUID(),
            )
        },
    )

    @Test
    fun `music album maps to Album`() {
        LibraryItem.from(dto(BaseItemKind.MUSIC_ALBUM)).shouldBeInstanceOf<LibraryItem.Album>()
    }

    @Test
    fun `audio book maps to AudioFile`() {
        LibraryItem.from(dto(BaseItemKind.AUDIO_BOOK)).shouldBeInstanceOf<LibraryItem.AudioFile>()
    }

    @Test
    fun `plain audio maps to AudioFile`() {
        LibraryItem.from(dto(BaseItemKind.AUDIO)).shouldBeInstanceOf<LibraryItem.AudioFile>()
    }

    @Test
    fun `unrecognized folder type maps to BrowseFolder`() {
        LibraryItem.from(
            dto(BaseItemKind.COLLECTION_FOLDER, isFolder = true),
        ).shouldBeInstanceOf<LibraryItem.BrowseFolder>()
    }

    @Test
    fun `unrecognized non-folder type falls back to AudioFile`() {
        LibraryItem.from(dto(BaseItemKind.FOLDER, isFolder = false)).shouldBeInstanceOf<LibraryItem.AudioFile>()
    }

    @Test
    fun `album display name prefers the filesystem folder name over the album tag`() {
        val item = LibraryItem.Album(
            dto(BaseItemKind.MUSIC_ALBUM, name = "Album Tag Name", path = "/audiobooks/My Book/"),
        )
        item.displayName shouldBe "My Book"
    }

    @Test
    fun `album display name falls back to the name field when there is no path`() {
        val item = LibraryItem.Album(dto(BaseItemKind.MUSIC_ALBUM, name = "Album Tag Name", path = null))
        item.displayName shouldBe "Album Tag Name"
    }

    @Test
    fun `folderNameFromPath handles trailing slashes and backslashes`() {
        LibraryItem.folderNameFromPath("C:\\Books\\My Book\\") shouldBe "My Book"
        LibraryItem.folderNameFromPath("/audiobooks/My Book") shouldBe "My Book"
        LibraryItem.folderNameFromPath(null) shouldBe null
        LibraryItem.folderNameFromPath("") shouldBe null
    }

    @Test
    fun `a book or album is finished once the server says it was played`() {
        LibraryItem.from(dto(BaseItemKind.AUDIO_BOOK, played = true)).isFinished shouldBe true
        LibraryItem.from(dto(BaseItemKind.MUSIC_ALBUM, played = true)).isFinished shouldBe true
        LibraryItem.from(dto(BaseItemKind.AUDIO_BOOK, played = false)).isFinished shouldBe false
        LibraryItem.from(dto(BaseItemKind.AUDIO_BOOK)).isFinished shouldBe false
    }

    @Test
    fun `a book of several files is finished once every file was played (its progress is full)`() {
        fun bundle(progress: Float?) = LibraryItem.AudiobookBundle(
            id = UUID.randomUUID(), displayName = "Book", description = "", imageItemId = null, imageTag = null,
            runTimeTicks = null, progressPercent = progress, trackIds = emptyList(), isBundle = true, parentFolderId = null,
        )
        bundle(100f).isFinished shouldBe true
        bundle(99f).isFinished shouldBe false
        bundle(null).isFinished shouldBe false
    }

    @Test
    fun `a folder is never finished`() {
        LibraryItem.from(dto(BaseItemKind.COLLECTION_FOLDER, isFolder = true, played = true)).isFinished shouldBe false
    }

    @Test
    fun `a book or album's author is its writer (the artist), not the narrator (the album artist)`() {
        fun narrated(type: BaseItemKind) = BaseItemDto(
            id = UUID.randomUUID(),
            type = type,
            albumArtist = "Stephen Fry",
            artists = listOf("J.K. Rowling"),
        )
        LibraryItem.from(narrated(BaseItemKind.AUDIO_BOOK)).description shouldBe "J.K. Rowling"
        LibraryItem.from(narrated(BaseItemKind.MUSIC_ALBUM)).description shouldBe "J.K. Rowling"
    }

    @Test
    fun `sorting by author sorts by writer`() {
        SortOption.AUTHOR.sortBy shouldBe ItemSortBy.ARTIST
    }
}
