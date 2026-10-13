package org.jellyfin.mobile.books.library

import org.jellyfin.mobile.books.data.entity.BookProgressEntity
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
        val itemId = book.id?.toString() ?: return@filter true
        val bookProgress = progressByItemId[itemId]
        when (progressFilter) {
            ProgressFilter.ALL -> true
            ProgressFilter.UNREAD -> bookProgress == null || bookProgress.pageNumber == 0
            ProgressFilter.IN_PROGRESS ->
                bookProgress != null &&
                    bookProgress.pageNumber > 0 &&
                    bookProgress.totalPages > 0 &&
                    bookProgress.pageNumber < bookProgress.totalPages - 1
            ProgressFilter.FINISHED ->
                bookProgress != null &&
                    bookProgress.totalPages > 0 &&
                    bookProgress.pageNumber >= bookProgress.totalPages - 1
        }
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
