package org.jellyfin.mobile.common.ui

/**
 * Test tags for the parts of a screen that tests find by role rather than by text: unit tests
 * and the on-device e2e tests share them, and so do the apps.
 */
object VaultTestTags {
    /** A library shown as a list. */
    const val LIBRARY_LIST = "library_list"

    /** A library shown as a grid or shelf. */
    const val LIBRARY_GRID = "library_grid"

    /** One item in [LIBRARY_LIST] or [LIBRARY_GRID]. */
    const val LIBRARY_ITEM = "library_item"

    /** The mark on an item that's downloaded to the device. */
    const val DOWNLOADED_MARK = "downloaded_mark"

    /** A screen's search box. */
    const val SEARCH_FIELD = "search_field"

    /** The "downloaded only" switch in a library's menu. */
    const val DOWNLOADED_ONLY = "downloaded_only"

    /** A screen's spinner while its content loads. */
    const val LOADING = "loading"
}
