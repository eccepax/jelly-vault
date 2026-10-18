package org.jellyfin.mobile.books.reader

/**
 * How far the pages are zoomed in, and where: drawn at [scale] from the top-left corner, then moved
 * by ([x], [y]). Never smaller than the screen, and the page always covers it (no gaps at the edges).
 */
class PageZoom {
    var scale = 1f
        private set
    var x = 0f
        private set
    var y = 0f
        private set

    val isZoomed: Boolean get() = scale > 1f

    private var width = 0f
    private var height = 0f

    fun setViewport(width: Float, height: Float) {
        this.width = width
        this.height = height
        clamp()
    }

    /** A pinch: zooms by [factor], keeping the page point under ([focusX], [focusY]) where it is. */
    fun zoomBy(factor: Float, focusX: Float, focusY: Float) {
        zoomTo(scale * factor, focusX, focusY)
    }

    /** A drag: moves the page by ([dx], [dy]), as far as its edges allow. */
    fun panBy(dx: Float, dy: Float) {
        x += dx
        y += dy
        clamp()
    }

    /** A double tap: zooms in on that spot, or back out if zoomed in already. */
    fun toggle(focusX: Float, focusY: Float) {
        if (isZoomed) reset() else zoomTo(DOUBLE_TAP_SCALE, focusX, focusY)
    }

    /**
     * A pinch has ended: barely zoomed in snaps back to the page's own size. Android stops counting a
     * pinch once the fingers are close together, so zooming out by hand tends to stop just short.
     */
    fun settle() {
        if (scale < SNAP_BACK_BELOW) reset()
    }

    fun reset() {
        scale = 1f
        x = 0f
        y = 0f
    }

    private fun zoomTo(target: Float, focusX: Float, focusY: Float) {
        val newScale = target.coerceIn(1f, MAX_SCALE)
        // The page point under the focus, then where it has to go to stay under it
        val pageX = (focusX - x) / scale
        val pageY = (focusY - y) / scale
        scale = newScale
        x = focusX - pageX * newScale
        y = focusY - pageY * newScale
        clamp()
    }

    private fun clamp() {
        x = x.coerceIn(width - width * scale, 0f)
        y = y.coerceIn(height - height * scale, 0f)
    }

    companion object {
        const val MAX_SCALE = 4f
        const val DOUBLE_TAP_SCALE = 2.5f
        private const val SNAP_BACK_BELOW = 1.1f
    }
}
