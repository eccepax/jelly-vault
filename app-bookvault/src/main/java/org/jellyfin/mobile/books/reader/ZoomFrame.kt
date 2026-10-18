package org.jellyfin.mobile.books.reader

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.view.children
import kotlin.math.abs

/**
 * Pinch (or double tap) to zoom the pages it holds, then drag to move around them. While zoomed out
 * it stays out of the way, so the pages scroll and swipe as usual.
 */
class ZoomFrame @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    /** Off for an EPUB, whose text sizes itself instead; turning it off zooms back out. */
    var zoomEnabled = false
        set(value) {
            field = value
            if (!value) reset()
        }

    /**
     * The pages scroll up and down themselves (a PDF as a scroll): a zoomed-in drag then only moves
     * them sideways, and an up-and-down one is left to scroll them.
     */
    var childScrollsVertically = false

    private val zoom = PageZoom()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom.zoomBy(detector.scaleFactor, detector.focusX, detector.focusY)
                apply()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                zoom.settle()
                apply()
            }
        },
    ).apply { isQuickScaleEnabled = false }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                zoom.toggle(e.x, e.y)
                apply()
                return true
            }
        },
    )

    fun reset() {
        zoom.reset()
        apply()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        zoom.setViewport(w.toFloat(), h.toFloat())
        apply()
    }

    // The gestures see every touch, whoever ends up handling it
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (zoomEnabled) {
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!zoomEnabled) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                false
            }
            // A second finger: a pinch, so the pages shouldn't scroll or swipe under it
            MotionEvent.ACTION_POINTER_DOWN -> true
            MotionEvent.ACTION_MOVE -> takesDrag(event).also { taken ->
                if (taken) {
                    lastX = event.x
                    lastY = event.y
                }
            }
            else -> false
        }
    }

    /** Zoomed in, a one-finger drag moves the pages, unless it's up and down and they scroll themselves. */
    private fun takesDrag(event: MotionEvent): Boolean {
        if (!zoom.isZoomed || event.pointerCount != 1) return false
        val dx = abs(event.x - downX)
        val dy = abs(event.y - downY)
        val dragging = dx > touchSlop || dy > touchSlop
        return dragging && !(childScrollsVertically && dy >= dx)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!zoomEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                zoom.panBy(event.x - lastX, if (childScrollsVertically) 0f else event.y - lastY)
                apply()
                lastX = event.x
                lastY = event.y
            }
            // Carry on from a finger still down, rather than jumping to it
            MotionEvent.ACTION_POINTER_UP -> {
                val remaining = if (event.actionIndex == 0) 1 else 0
                lastX = event.getX(remaining)
                lastY = event.getY(remaining)
            }
        }
        // Even with nothing under the finger to take it, the rest of the gesture comes here
        return true
    }

    // A scrolling page asks not to be interrupted, but a pinch still has to be able to take over
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (zoomEnabled) {
            parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
        } else {
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
    }

    private fun apply() {
        children.forEach { child ->
            child.pivotX = 0f
            child.pivotY = 0f
            child.scaleX = zoom.scale
            child.scaleY = zoom.scale
            child.translationX = zoom.x
            child.translationY = zoom.y
        }
    }
}
