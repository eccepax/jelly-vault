package org.jellyfin.mobile.books.reader

import android.app.Application
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ZoomFrameTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val page = View(context)
    private val frame = ZoomFrame(context).apply {
        addView(page)
        measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
        )
        layout(0, 0, WIDTH, HEIGHT)
    }
    private var time = SystemClock.uptimeMillis()

    private fun touch(action: Int, x: Float, y: Float, downTime: Long) {
        val event = MotionEvent.obtain(downTime, time, action, x, y, 0)
        frame.dispatchTouchEvent(event)
        event.recycle()
    }

    private fun tap(x: Float, y: Float) {
        val down = time
        touch(MotionEvent.ACTION_DOWN, x, y, down)
        time += TAP_MS
        touch(MotionEvent.ACTION_UP, x, y, down)
    }

    private fun doubleTap(x: Float, y: Float) {
        tap(x, y)
        time += TAP_MS
        tap(x, y)
        time += TAP_MS
    }

    /** A one-finger drag by ([dx], [dy]), in small steps like a real finger. */
    private fun drag(dx: Float, dy: Float) {
        val down = time
        val startX = WIDTH / 2f
        val startY = HEIGHT / 2f
        touch(MotionEvent.ACTION_DOWN, startX, startY, down)
        for (step in 1..DRAG_STEPS) {
            time += STEP_MS
            touch(MotionEvent.ACTION_MOVE, startX + dx * step / DRAG_STEPS, startY + dy * step / DRAG_STEPS, down)
        }
        time += STEP_MS
        touch(MotionEvent.ACTION_UP, startX + dx, startY + dy, down)
        time += ViewConfiguration.getDoubleTapTimeout() + TAP_MS
    }

    @Test
    fun `a double tap zooms the pages in on that spot, and another zooms them back out`() {
        frame.zoomEnabled = true

        doubleTap(500f, 1000f)
        page.scaleX shouldBe PageZoom.DOUBLE_TAP_SCALE
        page.scaleY shouldBe PageZoom.DOUBLE_TAP_SCALE
        page.translationX shouldBeLessThan 0f

        doubleTap(500f, 1000f)
        page.scaleX shouldBe 1f
        page.translationX shouldBe 0f
    }

    @Test
    fun `with zoom off (an EPUB), a double tap does nothing`() {
        frame.zoomEnabled = false

        doubleTap(500f, 1000f)

        page.scaleX shouldBe 1f
    }

    @Test
    fun `turning zoom off zooms back out`() {
        frame.zoomEnabled = true
        doubleTap(500f, 1000f)

        frame.zoomEnabled = false

        page.scaleX shouldBe 1f
        page.translationX shouldBe 0f
        page.translationY shouldBe 0f
    }

    @Test
    fun `reset zooms back out`() {
        frame.zoomEnabled = true
        doubleTap(500f, 1000f)

        frame.reset()

        page.scaleX shouldBe 1f
    }

    @Test
    fun `zoomed in, a drag moves the page around`() {
        frame.zoomEnabled = true
        doubleTap(500f, 1000f)
        val x = page.translationX
        val y = page.translationY

        drag(-200f, -300f)

        page.translationX shouldBe (x - 200f)
        page.translationY shouldBe (y - 300f)
    }

    @Test
    fun `when the pages scroll themselves (a PDF as a scroll), a drag only moves them sideways`() {
        frame.zoomEnabled = true
        frame.childScrollsVertically = true
        doubleTap(500f, 1000f)
        val x = page.translationX
        val y = page.translationY

        drag(-200f, -50f)

        page.translationX shouldBe (x - 200f)
        page.translationY shouldBe y
    }

    @Test
    fun `not zoomed in, a drag leaves the pages where they are`() {
        frame.zoomEnabled = true

        drag(-200f, -300f)

        page.translationX shouldBe 0f
        page.translationY shouldBe 0f
    }

    private companion object {
        const val WIDTH = 1000
        const val HEIGHT = 2000
        const val TAP_MS = 50L
        const val STEP_MS = 16L
        const val DRAG_STEPS = 10
    }
}
