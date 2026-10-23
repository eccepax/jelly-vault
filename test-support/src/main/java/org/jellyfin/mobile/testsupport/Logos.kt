package org.jellyfin.mobile.testsupport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import kotlin.math.abs

/** An app's logo, drawn at [size] pixels square, for checking what the sign-in screen shows. */
fun Context.drawLogo(@DrawableRes res: Int, size: Int): Bitmap {
    val logo = requireNotNull(ContextCompat.getDrawable(this, res))
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    logo.setBounds(0, 0, size, size)
    logo.draw(Canvas(bitmap))
    return bitmap
}

/**
 * How far apart two colors' hues are on the color wheel, in degrees (0 to 180). Both must be
 * opaque and colorful: white, gray, black and see-through pixels all read as hue 0, so comparing
 * one would pass for any red accent.
 */
fun hueDistance(@ColorInt a: Int, @ColorInt b: Int): Float {
    requireHue(a)
    requireHue(b)
    val d = abs(hsv(a)[0] - hsv(b)[0])
    return minOf(d, FULL_CIRCLE - d)
}

/** [color]'s HSL lightness, from 0 (black) to 1 (white). */
fun lightness(@ColorInt color: Int): Float = FloatArray(HSL_SIZE).also { ColorUtils.colorToHSL(color, it) }[2]

/** [color]'s HSV saturation, from 0 (gray) to 1 (pure color). */
fun saturation(@ColorInt color: Int): Float = hsv(color)[1]

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

private fun requireHue(@ColorInt color: Int) {
    val (_, s, v) = hsv(color)
    require(Color.alpha(color) > OPAQUE && s >= MIN_SATURATION && v >= MIN_VALUE) {
        "#%08x has no hue to compare: it is see-through, gray, white or black".format(color)
    }
}

private fun hsv(@ColorInt color: Int): FloatArray = FloatArray(HSV_SIZE).also { Color.colorToHSV(color, it) }

private const val FULL_CIRCLE = 360f
private const val QUARTERS = 4
private const val HSV_SIZE = 3
private const val HSL_SIZE = 3
private const val OPAQUE = 200
private const val NEAR_WHITE = 225
private const val MIN_SATURATION = 0.25f
private const val MIN_VALUE = 0.25f
