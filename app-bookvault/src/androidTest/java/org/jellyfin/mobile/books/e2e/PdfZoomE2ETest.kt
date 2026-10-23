package org.jellyfin.mobile.books.e2e

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.jellyfin.mobile.books.R
import org.jellyfin.mobile.books.reader.PageZoomScale
import org.jellyfin.mobile.books.reader.READER_PAGES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.jellyfin.mobile.common.R as CommonR

/**
 * A PDF (the first book by title on the test server) zooms with a real two-finger pinch, and offers
 * no text settings: its pages are pictures.
 */
@ReadingArea
class PdfZoomE2ETest : LoggedInE2ETest() {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun openThePdf() {
        loginAndWaitForLibrary()
        openBookAtPosition(0)
        waitForPdfPage()
    }

    private fun pages() = composeRule.onNodeWithTag(READER_PAGES).fetchSemanticsNode()

    private fun scale(): Float = pages().config[PageZoomScale]

    /** Two fingers from [startGap] apart to [endGap] apart, around the middle of the pages. */
    private fun pinch(startGap: Float, endGap: Float) {
        val window = IntArray(2)
        composeRule.activityRule.scenario.onActivity { it.window.decorView.getLocationOnScreen(window) }
        // Where the pages sit, not where they're drawn: once zoomed, they're scaled and moved
        val bounds = pages().boundsInWindow
        val centerX = window[0] + bounds.center.x
        val centerY = window[1] + bounds.center.y
        val down = SystemClock.uptimeMillis()
        var time = down

        fun send(action: Int, gap: Float, pointers: Int = 2) {
            val properties = Array(pointers) { id ->
                MotionEvent.PointerProperties().apply {
                    this.id = id
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
            val coords = Array(pointers) { id ->
                MotionEvent.PointerCoords().apply {
                    x = centerX + if (id == 0) -gap / 2 else gap / 2
                    y = centerY
                    pressure = 1f
                    size = 1f
                }
            }
            val event = MotionEvent.obtain(down, time, action, pointers, properties, coords, 0, 0, 1f, 1f, 0, 0, 0, 0)
            instrumentation.sendPointerSync(event)
            event.recycle()
        }

        send(MotionEvent.ACTION_DOWN, startGap, pointers = 1)
        time += STEP_MS
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), startGap)
        for (step in 1..PINCH_STEPS) {
            time += STEP_MS
            send(MotionEvent.ACTION_MOVE, startGap + (endGap - startGap) * step / PINCH_STEPS)
        }
        time += STEP_MS
        send(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), endGap)
        time += STEP_MS
        send(MotionEvent.ACTION_UP, endGap, pointers = 1)
        instrumentation.waitForIdleSync()
    }

    @Test
    fun pinchingAPdfZoomsItsPagesInAndBackOut() {
        openThePdf()
        assertEquals("at the page's own size", 1f, scale())

        // Wider than Android's smallest pinch (ViewConfiguration.scaledMinimumScalingSpan, ~27mm),
        // below which it doesn't count as one
        pinch(startGap = 500f, endGap = 1000f)
        assertTrue("zoomed in, at ${scale()}", scale() > 1.5f)

        pinch(startGap = 1000f, endGap = 100f)
        assertEquals("zoomed back out", 1f, scale())
    }

    @Test
    fun aPdfOffersNoTextSettings() {
        openThePdf()

        val context = instrumentation.targetContext
        composeRule.onNodeWithContentDescription(context.getString(CommonR.string.vault_more_options)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.bookmarks_title)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.text_settings_title)).assertDoesNotExist()
    }

    private companion object {
        const val STEP_MS = 20L
        const val PINCH_STEPS = 10
    }
}
