package org.jellyfin.mobile.books.reader

import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import org.junit.Test

class PageZoomTest {

    private fun zoom() = PageZoom().apply { setViewport(width = 1000f, height = 2000f) }

    @Test
    fun `it starts at the page's own size`() {
        val zoom = zoom()

        zoom.scale shouldBe 1f
        zoom.x shouldBe 0f
        zoom.y shouldBe 0f
        zoom.isZoomed shouldBe false
    }

    @Test
    fun `pinching keeps the point between the fingers where it was`() {
        val zoom = zoom()

        zoom.zoomBy(2f, focusX = 500f, focusY = 1000f)

        zoom.scale shouldBe 2f
        // The page point under (500, 1000) is still drawn at (500, 1000)
        (500f - zoom.x) / zoom.scale shouldBe (500f plusOrMinus 0.01f)
        (1000f - zoom.y) / zoom.scale shouldBe (1000f plusOrMinus 0.01f)
        zoom.isZoomed shouldBe true
    }

    @Test
    fun `it can't zoom out past the page's own size`() {
        val zoom = zoom()

        zoom.zoomBy(0.5f, focusX = 500f, focusY = 1000f)

        zoom.scale shouldBe 1f
        zoom.x shouldBe 0f
        zoom.y shouldBe 0f
    }

    @Test
    fun `it can't zoom in past the most it allows`() {
        val zoom = zoom()

        zoom.zoomBy(100f, focusX = 0f, focusY = 0f)

        zoom.scale shouldBe PageZoom.MAX_SCALE
    }

    @Test
    fun `zooming in at a corner keeps the page covering the screen`() {
        val zoom = zoom()

        zoom.zoomBy(2f, focusX = 0f, focusY = 0f)

        zoom.x shouldBe 0f
        zoom.y shouldBe 0f
    }

    @Test
    fun `zoomed in, dragging moves the page, as far as its edges`() {
        val zoom = zoom()
        zoom.zoomBy(2f, focusX = 0f, focusY = 0f)

        zoom.panBy(-300f, -400f)
        zoom.x shouldBe -300f
        zoom.y shouldBe -400f

        // The page is 2000 x 4000 now: it can go no further left than -1000, or up than -2000
        zoom.panBy(-5000f, -5000f)
        zoom.x shouldBe -1000f
        zoom.y shouldBe -2000f

        zoom.panBy(5000f, 5000f)
        zoom.x shouldBe 0f
        zoom.y shouldBe 0f
    }

    @Test
    fun `at the page's own size, dragging doesn't move it`() {
        val zoom = zoom()

        zoom.panBy(-300f, -400f)

        zoom.x shouldBe 0f
        zoom.y shouldBe 0f
    }

    @Test
    fun `zooming back out pulls the page back over the screen`() {
        val zoom = zoom()
        zoom.zoomBy(3f, focusX = 1000f, focusY = 2000f)
        zoom.x shouldBe -2000f

        zoom.zoomBy(0.5f, focusX = 0f, focusY = 0f)

        zoom.scale shouldBe 1.5f
        // The page is 1500 wide: at most 500 of it can be off to the left
        zoom.x shouldBe -500f
        zoom.y shouldBe -1000f
    }

    @Test
    fun `a double tap zooms in on that spot, and another zooms back out`() {
        val zoom = zoom()

        zoom.toggle(focusX = 500f, focusY = 1000f)
        zoom.scale shouldBe PageZoom.DOUBLE_TAP_SCALE
        (500f - zoom.x) / zoom.scale shouldBe (500f plusOrMinus 0.01f)

        zoom.toggle(focusX = 500f, focusY = 1000f)
        zoom.scale shouldBe 1f
        zoom.x shouldBe 0f
        zoom.y shouldBe 0f
    }

    @Test
    fun `a pinch that ends barely zoomed in settles back to the page's own size`() {
        val zoom = zoom()
        zoom.zoomBy(1.05f, focusX = 500f, focusY = 1000f)

        zoom.settle()

        zoom.scale shouldBe 1f
        zoom.x shouldBe 0f
        zoom.y shouldBe 0f
    }

    @Test
    fun `a pinch that ends properly zoomed in stays there`() {
        val zoom = zoom()
        zoom.zoomBy(1.5f, focusX = 500f, focusY = 1000f)

        zoom.settle()

        zoom.scale shouldBe 1.5f
    }

    @Test
    fun `reset goes back to the page's own size`() {
        val zoom = zoom()
        zoom.zoomBy(2f, focusX = 500f, focusY = 500f)

        zoom.reset()

        zoom.scale shouldBe 1f
        zoom.x shouldBe 0f
        zoom.y shouldBe 0f
        zoom.isZoomed shouldBe false
    }

    @Test
    fun `a smaller screen (a rotation) keeps the page covering it`() {
        val zoom = zoom()
        zoom.zoomBy(2f, focusX = 1000f, focusY = 2000f)
        zoom.x shouldBe -1000f

        zoom.setViewport(width = 400f, height = 800f)

        zoom.x shouldBe -400f
        zoom.y shouldBe -800f
    }
}
