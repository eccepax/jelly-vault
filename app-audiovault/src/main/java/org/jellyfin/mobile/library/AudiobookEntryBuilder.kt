package org.jellyfin.mobile.library

import org.jellyfin.mobile.data.entity.AudiobookEntryEntity
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

/**
 * Groups raw Jellyfin items into [AudiobookEntryEntity] rows for the global flat library view.
 *
 * Kept separate from [LibraryViewModel] so it can be unit tested directly: resume position and the
 * progress bar both depend on this grouping, and Jellyfin often omits `parentId`.
 *
 * Grouping rules:
 * - Primary key: parent directory from [BaseItemDto.path] (falling back to `parentId`, then
 *   `"root"` if both are missing).
 * - Subgroup by album tag within each parent directory, to detect multi-chapter books vs.
 *   separate books stored in the same author/category folder.
 * - Same parent dir + same album + more than one file → collapsed into one bundle entry.
 * - Same parent dir + different album tags → each is a standalone entry.
 * - Single file in its directory → standalone entry.
 */
internal object AudiobookEntryBuilder {

    /** The folder [item] is in, from its path or else its parent id; null when the server gave neither. */
    fun parentDirOf(item: BaseItemDto): String? =
        item.path?.replace('\\', '/')?.trimEnd('/')?.substringBeforeLast('/')?.takeIf { it.isNotEmpty() }
            ?: item.parentId?.toString()

    /** [item]'s album tag, which tells apart separate books stored in the same folder. */
    fun albumKeyOf(item: BaseItemDto): String = item.album?.lowercase()?.trim() ?: ""

    /** A book's tracks in play order: by track number, then name. */
    val trackOrder: Comparator<BaseItemDto> = compareBy({ it.indexNumber ?: 0 }, { it.name.orEmpty() })

    fun build(allItems: List<BaseItemDto>, serverId: Long): List<AudiobookEntryEntity> {
        val result = mutableListOf<AudiobookEntryEntity>()

        // Primary grouping key: parent directory from path, falling back to parentId string
        val byParentDir = allItems.groupBy { item -> parentDirOf(item) ?: "root" }

        for ((parentDirKey, items) in byParentDir) {
            // Subgroup by album tag to detect multi-chapter books vs separate books in same dir
            val byAlbum = items.groupBy(::albumKeyOf)
            for ((albumKey, albumItems) in byAlbum) {
                val firstParentId = albumItems.firstOrNull()?.parentId
                if (albumItems.size == 1) {
                    result.add(albumItems.first().toStandaloneEntry(serverId))
                } else {
                    result.add(albumItems.toBundleEntry(parentDirKey, albumKey, firstParentId, serverId))
                }
            }
        }

        return result
    }

    private fun BaseItemDto.toStandaloneEntry(serverId: Long) = AudiobookEntryEntity(
        id = "S:$id",
        serverId = serverId,
        parentId = parentId?.toString(),
        title = name ?: "Unknown",
        author = albumArtist ?: artists?.firstOrNull(),
        imageItemId = id?.toString(),
        imageTag = imageTags?.get(ImageType.PRIMARY),
        runtimeTicks = runTimeTicks ?: 0L,
        trackIds = id?.toString() ?: "",
        isBundle = false,
        dateAdded = dateCreated?.toString() ?: "",
        lastPlayed = userData?.lastPlayedDate?.toString() ?: "",
        playedPercentage = userData?.playedPercentage?.toFloat() ?: 0f,
    )

    private fun List<BaseItemDto>.toBundleEntry(
        parentDirKey: String,
        albumKey: String,
        parentId: UUID?,
        serverId: Long,
    ): AudiobookEntryEntity {
        val first = first()
        // Bug: previously called folderNameFromPath(first.path) directly, which extracts the
        // LAST path segment — the track's own filename (e.g. "ch1.m4b"), not its parent folder.
        // Normalize to the parent directory first, matching the grouping key computation above.
        val parentDirName = first.path
            ?.replace('\\', '/')
            ?.trimEnd('/')
            ?.substringBeforeLast('/')
            ?.let(LibraryItem::folderNameFromPath)
        val title = first.album?.takeIf { it.isNotBlank() }
            ?: parentDirName
            ?: "Unknown"
        val ordered = sortedWith(trackOrder)
        val trackIds = ordered.mapNotNull { it.id?.toString() }.joinToString(",")
        val totalTicks = ordered.sumOf { it.runTimeTicks ?: 0L }
        val playedPercentage = AudiobookProgress.playedPercentage(ordered)
        return AudiobookEntryEntity(
            id = "B:$parentDirKey:$albumKey",
            serverId = serverId,
            parentId = parentId?.toString(),
            title = title,
            author = first.albumArtist ?: first.artists?.firstOrNull(),
            imageItemId = first.id?.toString(),
            imageTag = first.imageTags?.get(ImageType.PRIMARY),
            runtimeTicks = totalTicks,
            trackIds = trackIds,
            isBundle = true,
            dateAdded = minOfOrNull { it.dateCreated?.toString() ?: "" } ?: "",
            lastPlayed = maxOfOrNull { it.userData?.lastPlayedDate?.toString() ?: "" } ?: "",
            playedPercentage = playedPercentage,
        )
    }
}
