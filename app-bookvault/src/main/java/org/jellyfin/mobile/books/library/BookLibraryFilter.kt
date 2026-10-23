package org.jellyfin.mobile.books.library

import org.jellyfin.mobile.books.data.entity.BookProgressEntity
import org.jellyfin.mobile.books.progress.ReadingState
import org.jellyfin.mobile.books.progress.readingState
import org.jellyfin.mobile.books.progress.serverProgress
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Filters and sorts the raw book list for [BookLibraryViewModel]. Takes the already-fetched
 * progress map rather than querying [org.jellyfin.mobile.books.data.dao.BookProgressDao] itself,
 * so it can be unit tested directly.
 */
internal fun filterAndSortBooks(
    books: List<BaseItemDto>,
    progressByItemId: Map<String, BookProgressEntity>,
    sort: BookSortOption,
    progressFilter: ProgressFilter,
    tag: String?,
): List<BaseItemDto> = books
    .filter { book ->
        if (progressFilter == ProgressFilter.ALL) return@filter true
        val local = progressByItemId[book.id?.toString() ?: return@filter true]
        // The place read most recently wins, here or on another device
        readingState(local, book.serverProgress()).matches(progressFilter)
    }
    .filter { book ->
        if (tag == null) {
            true
        } else {
            book.tags?.contains(tag) == true
        }
    }
    .sortedWith(
        when (sort) {
            BookSortOption.TITLE -> compareBy { it.name?.lowercase() }
            BookSortOption.AUTHOR -> compareBy { it.albumArtist?.lowercase() }
            BookSortOption.DATE_ADDED -> compareByDescending { it.dateCreated }
        },
    )

/** Distinct, sorted set of tags across all loaded books, for the tag filter dropdown. */
internal fun availableTags(books: List<BaseItemDto>): List<String> = books
    .flatMap { it.tags.orEmpty() }
    .distinct()
    .sorted()

private fun ReadingState.matches(filter: ProgressFilter): Boolean = when (filter) {
    ProgressFilter.ALL -> true
    ProgressFilter.UNREAD -> this == ReadingState.UNREAD
    ProgressFilter.IN_PROGRESS -> this == ReadingState.IN_PROGRESS
    ProgressFilter.FINISHED -> this == ReadingState.FINISHED
}
