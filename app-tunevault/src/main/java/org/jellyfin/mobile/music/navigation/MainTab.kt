package org.jellyfin.mobile.music.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import org.jellyfin.mobile.music.R

/** The bottom bar's destinations. */
enum class MainTab(val label: Int, val icon: ImageVector) {
    HOME(R.string.home_title, Icons.Filled.Home),
    LIBRARY(R.string.library_title, Icons.Filled.LibraryMusic),
    SEARCH(R.string.search_title, Icons.Filled.Search),
    ;

    companion object {
        /** Where the app opens, and where back from any other tab returns to. */
        val START = HOME
    }
}
