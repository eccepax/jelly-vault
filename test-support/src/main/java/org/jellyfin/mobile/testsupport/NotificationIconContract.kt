package org.jellyfin.mobile.testsupport

import android.app.Application
import android.graphics.Color
import androidx.annotation.DrawableRes
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * An app's notification icon (its downloads', and its playback's where it has one) is its animal as
 * a white silhouette on see-through, as Android draws a small icon: only the shape counts, so any
 * colour would just be lost. Each app with downloads subclasses this with its own icon.
 *
 * In :test-support's main source set, so test names can't contain spaces (see [LogoContract]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class NotificationIconContract(@DrawableRes iconRes: Int) {

    private val icon = ApplicationProvider.getApplicationContext<Application>().drawLogo(iconRes, SIZE)
    private val pixels = (0 until SIZE).flatMap { x -> (0 until SIZE).map { y -> icon.getPixel(x, y) } }

    @Test
    fun notificationIconIsSeeThroughAroundTheAnimal() {
        Color.alpha(icon.getPixel(0, 0)) shouldBe 0
        Color.alpha(icon.getPixel(SIZE - 1, SIZE - 1)) shouldBe 0
    }

    @Test
    fun notificationIconsAnimalIsWhite() {
        val shown = pixels.filter { Color.alpha(it) > MOSTLY_OPAQUE }
        shown.size shouldBeGreaterThan SIZE * SIZE / MIN_SHARE_OF_SQUARE
        // Near white: drawing a partly see-through edge rounds 255 down a little
        withClue("pixels that aren't white") {
            shown.filter { minOf(Color.red(it), Color.green(it), Color.blue(it)) < NEAR_WHITE }.shouldBeEmpty()
        }
    }

    private companion object {
        const val SIZE = 96
        const val MOSTLY_OPAQUE = 200
        const val MIN_SHARE_OF_SQUARE = 8 // the animal fills at least an eighth of the icon
        const val NEAR_WHITE = 240
    }
}
