package org.jellyfin.mobile.library

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

/**
 * Unified representation of an item in the library list.
 *
 * Wraps [BaseItemDto] and resolves display properties once so the adapter and fragment
 * never need to inspect [BaseItemDto.type] or [BaseItemDto.isFolder] directly.
 */
sealed class LibraryItem {
    abstract val id: UUID
    abstract val displayName: String
    abstract val description: String
    abstract val runTimeTicks: Long?
    abstract val progressPercent: Float?

    /** Listened to the end: the server has it played (every file of a book of several). */
    abstract val isFinished: Boolean

    /**
     * The item ID to use when loading cover art, or null for items that show a generic icon.
     * For [Album] this is the MusicAlbum's own ID so album art is loaded.
     */
    abstract val imageItemId: UUID?

    // -------------------------------------------------------------------------
    // Subtypes
    // -------------------------------------------------------------------------

    /**
     * A MusicAlbum — a folder of audio tracks representing one audiobook.
     * Display name uses the filesystem folder name (last path component) so it matches
     * what the user sees on disk, not whatever the ID3 album tag says.
     */
    data class Album(val dto: BaseItemDto) : LibraryItem() {
        override val id: UUID get() = dto.id!!
        override val displayName: String
            get() = folderNameFromPath(dto.path) ?: dto.name ?: "Unknown"

        // The writer: Jellyfin keeps audiobook narrators as the album artist
        override val description: String
            get() = dto.artists?.firstOrNull() ?: dto.albumArtist ?: ""
        override val runTimeTicks: Long? get() = dto.runTimeTicks
        override val progressPercent: Float? get() = dto.userData?.playedPercentage?.toFloat()?.takeIf { it > 0f }
        override val isFinished: Boolean get() = dto.userData?.played == true
        override val imageItemId: UUID? get() = dto.id // load the album's own cover art
    }

    /**
     * A single playable audio file (AudioBook, Audio).
     * Plays directly; siblings in the current folder are queued for autoplay.
     */
    data class AudioFile(val dto: BaseItemDto) : LibraryItem() {
        override val id: UUID get() = dto.id!!
        override val displayName: String get() = dto.name ?: "Unknown"
        override val description: String
            get() = dto.artists?.firstOrNull() ?: dto.albumArtist ?: dto.album ?: ""
        override val runTimeTicks: Long? get() = dto.runTimeTicks
        override val progressPercent: Float? get() = dto.userData?.playedPercentage?.toFloat()?.takeIf { it > 0f }
        override val isFinished: Boolean get() = dto.userData?.played == true
        override val imageItemId: UUID? get() = dto.id
    }

    /**
     * A navigable folder — tapping opens it, it does not play directly.
     * Uses the generic folder icon (imageItemId = null).
     */
    data class BrowseFolder(val dto: BaseItemDto) : LibraryItem() {
        override val id: UUID get() = dto.id!!
        override val displayName: String get() = dto.name ?: "Unknown"
        override val description: String get() = ""
        override val runTimeTicks: Long? get() = null
        override val progressPercent: Float? get() = null
        override val isFinished: Boolean get() = false
        override val imageItemId: UUID? get() = null
    }

    /**
     * A client-side bundled audiobook — either a folder of chapter files collapsed into a
     * single entry (isBundle=true) or a standalone audio file shown in the flat list (isBundle=false).
     *
     * Only produced by [org.jellyfin.mobile.library.LibraryViewModel.loadGlobalFlat]; never
     * appears when the global flatten setting is off.
     *
     * When [isBundle] is true, [id] is the Jellyfin parentId of the chapter folder and
     * [trackIds] contains all chapter file IDs in playback order. When [isBundle] is false,
     * [id] is the file's own Jellyfin ID and [trackIds] contains exactly that one ID.
     */
    data class AudiobookBundle(
        override val id: UUID,
        override val displayName: String,
        override val description: String,
        override val imageItemId: UUID?,
        val imageTag: String?,
        override val runTimeTicks: Long?,
        override val progressPercent: Float?,
        val trackIds: List<UUID>,
        val isBundle: Boolean,
        /** Jellyfin folder UUID for the parent directory, when available. Used for resume via the album queue API. */
        val parentFolderId: UUID?,
        /** Its library entry's id: for a book of several files, the key to show it as separate books. */
        val entryId: String = "",
        /** The series it's in, when a book of several files is shown as separate books (see AudiobookEntryEntity). */
        val series: String? = null,
        val seriesIndex: Float? = null,
        /** The entry id of the one book its series is otherwise, to show it so again; null outside a series. */
        val seriesKey: String? = null,
    ) : LibraryItem() {
        // Its progress runs to where it resumes (see AudiobookProgress): full once every file was played
        override val isFinished: Boolean get() = (progressPercent ?: 0f) >= FINISHED_PERCENT
    }

    /**
     * Books grouped together (see [groupItems]): an author's, or a series'. Opening it shows them;
     * its [displayName] is the author or series, blank for books with no author.
     */
    data class Group(
        override val id: UUID,
        override val displayName: String,
        val count: Int,
        override val imageItemId: UUID?,
    ) : LibraryItem() {
        override val description: String get() = ""
        override val runTimeTicks: Long? get() = null
        override val progressPercent: Float? get() = null
        override val isFinished: Boolean get() = false
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    companion object {
        fun from(dto: BaseItemDto): LibraryItem = when (dto.type) {
            BaseItemKind.MUSIC_ALBUM -> Album(dto)
            BaseItemKind.AUDIO_BOOK, BaseItemKind.AUDIO -> AudioFile(dto)
            else -> if (dto.isFolder == true) BrowseFolder(dto) else AudioFile(dto)
        }

        /**
         * Extracts the leaf folder name from a filesystem path.
         * Handles both forward and back slashes, and strips trailing slashes.
         * Returns null if the path is null or yields an empty string.
         */
        internal fun folderNameFromPath(path: String?): String? =
            path?.replace('\\', '/')
                ?.trimEnd('/')
                ?.substringAfterLast('/')
                ?.takeIf { it.isNotEmpty() }
    }
}

/** A book's progress once it's listened to the end. */
internal const val FINISHED_PERCENT = 100f
