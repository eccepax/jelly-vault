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

    /** The mark on an item whose download waits for Wi-Fi. */
    const val WAITING_FOR_WIFI_MARK = "waiting_for_wifi_mark"

    /** A screen's search box. */
    const val SEARCH_FIELD = "search_field"

    /** The "downloaded only" switch in a library's menu. */
    const val DOWNLOADED_ONLY = "downloaded_only"

    /** A screen's spinner while its content loads. */
    const val LOADING = "loading"

    /** The sign-in screen's username field. */
    const val LOGIN_USERNAME = "login_username"

    /** The sign-in screen's password field. */
    const val LOGIN_PASSWORD = "login_password"

    /** The warning that signing in would cross the internet unencrypted. */
    const val LOGIN_INSECURE_SERVER = "login_insecure_server"

    /** The Quick Connect code waiting to be approved. */
    const val QUICK_CONNECT_CODE = "quick_connect_code"

    /** The bar along the bottom of a library while something is loaded. */
    const val MINI_PLAYER = "mini_player"
}
