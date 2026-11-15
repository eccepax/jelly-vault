package org.jellyfin.mobile.library

import java.util.UUID

/** How the whole library's books are grouped: not at all, by author, or by series. */
enum class GroupBy {
    NONE,
    AUTHOR,
    SERIES,
    ;

    companion object {
        fun fromName(name: String?): GroupBy = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/**
 * The library's [items] grouped [by] author or by series: a [LibraryItem.Group] each, by name (books
 * with no author last; by series, the books in none follow the series). With a group [open], its books
 * instead: a series' in the series' order, an author's as they came.
 */
internal fun groupItems(items: List<LibraryItem>, by: GroupBy, open: String?): List<LibraryItem> {
    val nameOf: (LibraryItem) -> String? = when (by) {
        GroupBy.NONE -> return items
        GroupBy.AUTHOR -> { item -> item.description }
        GroupBy.SERIES -> { item -> (item as? LibraryItem.AudiobookBundle)?.series }
    }
    if (open != null) {
        val books = items.filter { nameOf(it) == open }
        return if (by == GroupBy.SERIES) books.sortedBy { (it as LibraryItem.AudiobookBundle).seriesIndex } else books
    }
    val (grouped, ungrouped) = items.partition { nameOf(it) != null }
    val groups = grouped.groupBy { nameOf(it)!! }
        .map { (name, books) ->
            val ordered = if (by == GroupBy.SERIES) books.sortedBy { (it as LibraryItem.AudiobookBundle).seriesIndex } else books
            LibraryItem.Group(
                id = UUID.nameUUIDFromBytes("group:$by:$name".toByteArray()),
                displayName = name,
                count = books.size,
                imageItemId = ordered.firstNotNullOfOrNull { it.imageItemId },
            )
        }
        .sortedWith(compareBy({ it.displayName.isBlank() }, { it.displayName.lowercase() }))
    return groups + ungrouped
}
