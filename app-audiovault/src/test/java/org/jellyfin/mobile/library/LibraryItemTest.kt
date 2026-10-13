package org.jellyfin.mobile.library

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.jupiter.api.Test
import java.util.UUID

/** Basic functionality coverage for mapping raw Jellyfin items to [LibraryItem] subtypes. */
class LibraryItemTest {

    private fun dto(
        type: BaseItemKind,
        isFolder: Boolean? = null,
        name: String? = "Item Name",
        path: String? = null,
    ): BaseItemDto = BaseItemDto(id = UUID.randomUUID(), type = type, isFolder = isFolder, name = name, path = path)

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
}
