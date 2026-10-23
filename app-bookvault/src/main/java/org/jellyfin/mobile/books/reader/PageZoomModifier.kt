package org.jellyfin.mobile.books.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** How far the pages are zoomed in: 1 at their own size. */
val PageZoomScale = SemanticsPropertyKey<Float>("PageZoomScale")
private var SemanticsPropertyReceiver.pageZoomScale by PageZoomScale

/** A [PageZoom] whose every change is shown: the pages are drawn from it. */
@Stable
class PageZoomState {
    private val zoom = PageZoom()

    var scale by mutableFloatStateOf(1f)
        private set
    var x by mutableFloatStateOf(0f)
        private set
    var y by mutableFloatStateOf(0f)
        private set

    val isZoomed: Boolean get() = scale > 1f

    fun setViewport(width: Float, height: Float) = change { setViewport(width, height) }

    fun zoomBy(factor: Float, focus: Offset) = change { zoomBy(factor, focus.x, focus.y) }

    fun panBy(dx: Float, dy: Float) = change { panBy(dx, dy) }

    fun toggle(focus: Offset) = change { toggle(focus.x, focus.y) }

    fun settle() = change { settle() }

    fun reset() = change { reset() }

    private fun change(change: PageZoom.() -> Unit) {
        zoom.change()
        scale = zoom.scale
        x = zoom.x
        y = zoom.y
    }
}

/**
 * Pinch (or double tap) to zoom the pages, then drag to move around them; while [enabled] is off
 * (an EPUB, whose text sizes itself) nothing zooms. Not zoomed in, every touch is left to the pages,
 * so they scroll and swipe as usual. Zoomed in, a one-finger drag moves them, unless they
 * [scrollVertically] (a PDF as a scroll) and it's up and down: that's left to scroll them, and a
 * drag only moves them sideways.
 */
fun Modifier.pageZoom(state: PageZoomState, enabled: Boolean, scrollVertically: Boolean): Modifier = this
    .onSizeChanged { state.setViewport(it.width.toFloat(), it.height.toFloat()) }
    .semantics { pageZoomScale = state.scale }
    .then(
        if (enabled) {
            Modifier.pointerInput(state, scrollVertically) { detectZoomGestures(state, scrollVertically) }
        } else {
            Modifier
        },
    )
    .clipToBounds()
    .graphicsLayer {
        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = state.scale
        scaleY = state.scale
        translationX = state.x
        translationY = state.y
    }

/**
 * Seen before the pages see them (the initial pass), so a pinch, or a drag moving zoomed-in pages,
 * is taken from them; taps, and drags it doesn't move them with, are left to them.
 */
private suspend fun PointerInputScope.detectZoomGestures(state: PageZoomState, scrollVertically: Boolean) {
    val taps = DoubleTaps(viewConfiguration.doubleTapTimeoutMillis, DOUBLE_TAP_DISTANCE.toPx())
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var dragged = Offset.Zero
        var moved = false
        var panning = false
        var pinched = false
        var upAt = down.uptimeMillis
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) {
                upAt = event.changes.maxOf { it.uptimeMillis }
                break
            }
            if (pressed.size > 1) {
                pinched = true
                state.zoomBy(event.calculateZoom(), event.calculateCentroid(useCurrent = true))
                event.changes.forEach { it.consume() }
            } else if (!pinched) {
                // A pinch ending with a finger still down doesn't turn into a drag
                val change = pressed.single()
                dragged += change.positionChange()
                if (!moved && dragged.getDistance() > viewConfiguration.touchSlop) {
                    moved = true
                    panning = state.isZoomed && !(scrollVertically && abs(dragged.y) >= abs(dragged.x))
                }
                if (panning) {
                    val delta = change.positionChange()
                    state.panBy(delta.x, if (scrollVertically) 0f else delta.y)
                    change.consume()
                }
            }
        }
        when {
            pinched -> state.settle()
            moved -> Unit
            taps.isSecond(down.uptimeMillis, upAt, down.position) -> state.toggle(down.position)
        }
    }
}

/** Taps, as they come: the second of two close together is a double tap. */
private class DoubleTaps(private val timeoutMillis: Long, private val maxDistance: Float) {
    private var lastUpAt: Long? = null
    private var lastPosition = Offset.Zero

    /** A tap from [downAt] to [upAt] at [position]: whether it's the second of a double tap. */
    fun isSecond(downAt: Long, upAt: Long, position: Offset): Boolean {
        val last = lastUpAt
        val second = last != null && downAt - last < timeoutMillis && (position - lastPosition).getDistance() < maxDistance
        // A double tap's second tap doesn't start another
        lastUpAt = if (second) null else upAt
        lastPosition = position
        return second
    }
}

private val DOUBLE_TAP_DISTANCE = 100.dp
