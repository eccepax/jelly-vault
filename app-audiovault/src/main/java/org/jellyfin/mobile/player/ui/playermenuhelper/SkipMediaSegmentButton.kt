package org.jellyfin.mobile.player.ui.playermenuhelper

import android.widget.Button
import androidx.core.view.isVisible
import org.jellyfin.sdk.model.api.MediaSegmentDto
import java.lang.ref.WeakReference

class SkipMediaSegmentButton(
    skipMediaSegmentButton: Button,
    callback: (mediaSegment: MediaSegmentDto?) -> Unit,
) {
    private val buttonRef = WeakReference(skipMediaSegmentButton)
    private var mediaSegment: MediaSegmentDto? = null

    init {
        skipMediaSegmentButton.setOnClickListener {
            callback(mediaSegment)
        }
    }

    fun showSkipSegmentButton(mediaSegmentDto: MediaSegmentDto) {
        mediaSegment = mediaSegmentDto
        buttonRef.get()?.isVisible = true
    }

    fun hideSkipSegmentButton() {
        mediaSegment = null
        buttonRef.get()?.isVisible = false
    }
}
