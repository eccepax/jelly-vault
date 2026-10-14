package org.jellyfin.mobile.testsupport

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Looper
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.takahirom.roborazzi.captureRoboImage
import org.robolectric.Shadows.shadowOf
import kotlin.math.roundToInt

/**
 * Screen size and theme for screenshot tests: a typical phone in dark mode. Use it together with
 * [SCREENSHOT_SDK] and `@GraphicsMode(GraphicsMode.Mode.NATIVE)` so Robolectric draws real pixels.
 */
const val PHONE_QUALIFIERS = "w393dp-h852dp-night-xhdpi"

/**
 * The Android version screenshots render on. Real drawing needs API 26+, and library modules would
 * otherwise run their tests on their minSdk; pinning it also keeps screenshots identical across modules.
 */
const val SCREENSHOT_SDK = 35

private const val STATUS_BAR_DP = 24
private const val NAVIGATION_BAR_DP = 48 // 3-button navigation: the tallest, fully opaque case

/**
 * Screenshots this screen as it looks on a phone with 3-button navigation, into
 * `src/test/screenshots/[name].png` of the module under test.
 *
 * Robolectric neither reports system bars to the app nor draws them, so a screen that ignores them
 * would look fine. This tells the screen about a status bar and a navigation bar, then paints them
 * solid over the result, the way a phone does: anything a screen leaves underneath disappears from
 * the screenshot.
 *
 * Record with `./gradlew recordRoborazziDebug` and check with `verifyRoborazziDebug`; a plain
 * `./gradlew test` run takes no screenshots.
 */
fun View.captureWithSystemBars(name: String) {
    // Capture the whole window content, not just this view: screens that make room for the bars
    // with margins (rather than padding) move themselves within it
    val window = rootView.findViewById<View>(android.R.id.content) ?: this
    val density = resources.displayMetrics.density
    val statusBar = (STATUS_BAR_DP * density).roundToInt()
    val navigationBar = (NAVIGATION_BAR_DP * density).roundToInt()

    val bars = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBar, 0, 0))
        .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navigationBar))
        .build()
    ViewCompat.dispatchApplyWindowInsets(window, bars)
    window.requestLayout()
    shadowOf(Looper.getMainLooper()).idle()

    val width = window.width
    val height = window.height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    // On a phone the window's background (from the app theme) shows through behind the screen
    rootView.background?.let { windowBackground ->
        windowBackground.setBounds(0, 0, width, height)
        windowBackground.draw(canvas)
    }
    window.draw(canvas)
    val barPaint = Paint().apply { color = Color.BLACK }
    canvas.drawRect(0f, 0f, width.toFloat(), statusBar.toFloat(), barPaint)
    canvas.drawRect(0f, (height - navigationBar).toFloat(), width.toFloat(), height.toFloat(), barPaint)

    bitmap.captureRoboImage("src/test/screenshots/$name.png")
}
