package org.jellyfin.mobile.library

import org.jellyfin.sdk.model.api.ItemSortBy

enum class SortOption(val sortBy: ItemSortBy) {
    TITLE(ItemSortBy.NAME),
    AUTHOR(ItemSortBy.ALBUM_ARTIST),
    DATE_ADDED(ItemSortBy.DATE_CREATED),
    LAST_PLAYED(ItemSortBy.DATE_PLAYED),
    ;

    companion object {
        fun fromName(name: String?): SortOption = entries.firstOrNull { it.name == name } ?: TITLE
    }
}
