package org.jellyfin.mobile.testsupport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import kotlin.math.abs

/** An app's logo, drawn at [size] pixels square, for checking what the sign-in screen shows. */
fun Context.drawLogo(@DrawableRes res: Int, size: Int): Bitmap {
    val logo = requireNotNull(ContextCompat.getDrawable(this, res))
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    logo.setBounds(0, 0, size, size)
    logo.draw(Canvas(bitmap))
    return bitmap
}

/** How far apart two colors' hues are on the color wheel, in degrees (0 to 180). */
fun hueDistance(@ColorInt a: Int, @ColorInt b: Int): Float {
    val d = abs(hue(a) - hue(b))
    return minOf(d, FULL_CIRCLE - d)
}

/** The share of the middle half of [bitmap] that is close to white: an app icon's animal. */
fun Bitmap.whiteShareOfMiddle(): Float {
    val from = width / QUARTERS
    val to = width * (QUARTERS - 1) / QUARTERS
    var white = 0
    for (y in from until to) {
        for (x in from until to) {
            val p = getPixel(x, y)
            if (Color.alpha(p) > OPAQUE && minOf(Color.red(p), Color.green(p), Color.blue(p)) > NEAR_WHITE) white++
        }
    }
    return white.toFloat() / ((to - from) * (to - from))
}

private fun hue(@ColorInt color: Int): Float = FloatArray(HSV_SIZE).also { Color.colorToHSV(color, it) }[0]

private const val FULL_CIRCLE = 360f
private const val QUARTERS = 4
private const val HSV_SIZE = 3
private const val OPAQUE = 200
private const val NEAR_WHITE = 225
