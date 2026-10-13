package org.jellyfin.mobile.player.ui.playermenuhelper

import org.jellyfin.mobile.player.ui.ChapterMarking
import java.lang.ref.WeakReference

class ChapterMarkings {
    var markings: List<WeakReference<ChapterMarking>> = emptyList()
        private set

    fun setMarkings(markings: List<ChapterMarking>) {
        this.markings = markings.map(::WeakReference)
    }
}
