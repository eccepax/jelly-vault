package org.jellyfin.mobile.books.library

import org.jellyfin.mobile.books.data.entity.BookProgressEntity
import org.jellyfin.mobile.books.progress.ServerBookProgress
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
        val server = book.serverProgress()
        val serverAt = server.knownAt
        // The place read most recently wins, here or on another device
        if (serverAt != null && (local == null || serverAt.toEpochMilli() > local.lastUpdated)) {
            server.matches(progressFilter)
        } else {
            local.matches(progressFilter)
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

private fun BookProgressEntity?.matches(filter: ProgressFilter): Boolean = when (filter) {
    ProgressFilter.ALL -> true
    ProgressFilter.UNREAD -> this == null || pageNumber == 0
    ProgressFilter.IN_PROGRESS ->
        this != null && pageNumber > 0 && totalPages > 0 && pageNumber < totalPages - 1
    ProgressFilter.FINISHED -> this != null && totalPages > 0 && pageNumber >= totalPages - 1
}

/** A book finished elsewhere is played with no place left in it (see [ServerBookProgress.page]). */
private fun ServerBookProgress.matches(filter: ProgressFilter): Boolean = when (filter) {
    ProgressFilter.ALL -> true
    ProgressFilter.UNREAD -> positionTicks == 0L && !played
    ProgressFilter.IN_PROGRESS -> positionTicks > 0L
    ProgressFilter.FINISHED -> positionTicks == 0L && played
}
