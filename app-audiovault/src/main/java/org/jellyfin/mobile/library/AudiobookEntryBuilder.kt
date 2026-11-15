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
 * - Sibling folders of one book's discs ("Just Mercy (Disc 1)", "(Disc 2)"…), each disc with its
 *   own album tag, count as one folder and one album: one book, disc by disc.
 * - A book of several files in [build]'s `separateBooks` is shown as separate books instead, one
 *   per file: a series (the album), in the files' order (see [AudiobookEntryEntity.series]).
 */
internal object AudiobookEntryBuilder {

    /**
     * The folder [item]'s book is in, from its path or else its parent id; null when the server gave
     * neither. A disc's folder ("Book (Disc 2)") stands for the book's ("Book"), shared by its discs.
     */
    fun parentDirOf(item: BaseItemDto): String? {
        val folder = item.path?.replace('\\', '/')?.trimEnd('/')?.substringBeforeLast('/')?.takeIf { it.isNotEmpty() }
            ?: return item.parentId?.toString()
        val disc = discOf(folder.substringAfterLast('/')) ?: return folder
        return folder.substringBeforeLast('/', missingDelimiterValue = "") + "/" + disc.first
    }

    /** [item]'s album tag, which tells apart separate books stored in the same folder; a disc's is its book's. */
    fun albumKeyOf(item: BaseItemDto): String = withoutDisc(item.album.orEmpty()).lowercase().trim()

    /**
     * Which book [item] is part of, as the library groups them (its folder and album tag), so every
     * part of a book has the same key whichever of them is asked for. A file the server gave no
     * folder for is a book of its own.
     */
    fun bookKeyOf(item: BaseItemDto): String = "${parentDirOf(item) ?: item.id}:${albumKeyOf(item)}"

    /** A book's tracks in play order: disc by disc, then by track number, then name. */
    val trackOrder: Comparator<BaseItemDto> =
        compareBy({ discNumberOf(it) }, { it.indexNumber ?: 0 }, { it.name.orEmpty() })

    /** "Just Mercy (Disc 2)" (or "- CD 2", "Disc2", "[Disk 2]") as "Just Mercy" and 2; null without a disc. */
    internal fun discOf(name: String): Pair<String, Int>? =
        DISC.matchEntire(name.trim())?.let { match ->
            match.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { it to match.groupValues[2].toInt() }
        }

    private fun withoutDisc(name: String): String = discOf(name)?.first ?: name

    /** Which disc [item] is on: the server's disc number, else its folder's or album's; 0 for none. */
    private fun discNumberOf(item: BaseItemDto): Int =
        item.parentIndexNumber
            ?: item.path?.replace('\\', '/')?.substringBeforeLast('/')?.substringAfterLast('/')?.let(::discOf)?.second
            ?: item.album?.let(::discOf)?.second
            ?: 0

    private val DISC = Regex("""^(.*?)[\s\-_]*[(\[]?\s*(?:disc|disk|cd)\s*(\d+)\s*[)\]]?$""", RegexOption.IGNORE_CASE)

    /**
     * The library's books from [allItems] (see the class for how files make books). [separateBooks]
     * are the keys (entry ids) of books of several files to show as separate books, a series.
     */
    fun build(
        allItems: List<BaseItemDto>,
        serverId: Long,
        separateBooks: Set<String> = emptySet(),
    ): List<AudiobookEntryEntity> {
        val result = mutableListOf<AudiobookEntryEntity>()

        // Primary grouping key: parent directory from path, falling back to parentId string
        val byParentDir = allItems.groupBy { item -> parentDirOf(item) ?: "root" }

        for ((parentDirKey, items) in byParentDir) {
            // Subgroup by album tag to detect multi-chapter books vs separate books in same dir
            val byAlbum = items.groupBy(::albumKeyOf)
            for ((albumKey, albumItems) in byAlbum) {
                val firstParentId = albumItems.firstOrNull()?.parentId
                val key = bundleKey(parentDirKey, albumKey)
                when {
                    albumItems.size == 1 -> result.add(albumItems.first().toStandaloneEntry(serverId))
                    key in separateBooks -> result.addAll(albumItems.toSeries(key, serverId))
                    else -> result.add(albumItems.toBundleEntry(parentDirKey, albumKey, firstParentId, serverId))
                }
            }
        }

        return result
    }

    private fun bundleKey(parentDirKey: String, albumKey: String) = "B:$parentDirKey:$albumKey"

    /** Each file its own book, in a series named after the album, numbered in the files' order. */
    private fun List<BaseItemDto>.toSeries(key: String, serverId: Long): List<AudiobookEntryEntity> {
        val ordered = sortedWith(trackOrder)
        val series = ordered.first().album?.let(::withoutDisc)?.takeIf { it.isNotBlank() }
        return ordered.mapIndexed { position, item ->
            item.toStandaloneEntry(serverId).copy(
                series = series ?: key,
                seriesIndex = (item.indexNumber ?: (position + 1)).toFloat(),
                seriesKey = key,
            )
        }
    }

    private fun BaseItemDto.toStandaloneEntry(serverId: Long) = AudiobookEntryEntity(
        id = "S:$id",
        serverId = serverId,
        parentId = parentId?.toString(),
        title = name ?: "Unknown",
        // The writer: Jellyfin keeps audiobook narrators as the album artist
        author = artists?.firstOrNull() ?: albumArtist,
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
        val title = first.album?.let(::withoutDisc)?.takeIf { it.isNotBlank() }
            ?: parentDirName?.let(::withoutDisc)
            ?: "Unknown"
        val ordered = sortedWith(trackOrder)
        val trackIds = ordered.mapNotNull { it.id?.toString() }.joinToString(",")
        val totalTicks = ordered.sumOf { it.runTimeTicks ?: 0L }
        val playedPercentage = AudiobookProgress.playedPercentage(ordered)
        return AudiobookEntryEntity(
            id = bundleKey(parentDirKey, albumKey),
            serverId = serverId,
            parentId = parentId?.toString(),
            title = title,
            author = first.artists?.firstOrNull() ?: first.albumArtist,
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
