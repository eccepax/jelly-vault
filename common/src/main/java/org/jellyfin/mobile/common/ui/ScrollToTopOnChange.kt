package org.jellyfin.mobile.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Takes a list back to the top, with [scrollToTop], whenever [key] changes: a new sort or filter
 * makes it a different list, and staying scrolled down would land somewhere arbitrary in it. A
 * screen that comes back (from an item, after rotating) with the same [key] keeps its place.
 */
@Composable
fun ScrollToTopOnChange(key: String, scrollToTop: suspend () -> Unit) {
    var shownFor by rememberSaveable { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (key != shownFor) {
            shownFor = key
            scrollToTop()
        }
    }
}
