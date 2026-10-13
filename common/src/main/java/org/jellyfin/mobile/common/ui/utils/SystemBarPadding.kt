package org.jellyfin.mobile.common.ui.utils

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

// The apps draw edge-to-edge, so each screen has to keep its content clear of the system bars.
// Both helpers add to the view's own padding rather than replacing it.

/** Pads the top of this view (typically a toolbar) by the status bar's height. */
fun View.padForStatusBar() {
    val baseTop = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        v.updatePadding(top = baseTop + insets.getInsets(WindowInsetsCompat.Type.statusBars()).top)
        insets
    }
}

/**
 * Pads the bottom and sides of this view (typically a screen's root) by the system bars, so the
 * navigation bar — fully opaque with 3-button navigation — doesn't cover buttons or the end of a list.
 */
fun View.padForNavigationBar() {
    val baseLeft = paddingLeft
    val baseRight = paddingRight
    val baseBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        v.updatePadding(left = baseLeft + bars.left, right = baseRight + bars.right, bottom = baseBottom + bars.bottom)
        insets
    }
}
