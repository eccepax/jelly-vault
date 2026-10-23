package org.jellyfin.mobile.testsupport

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.withClue
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeGreaterThanOrEqual
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sign-in screen's logo is the app's icon, and the app's accent comes from that icon: the
 * icon's hue, lightened so it reads on the dark background, and still vivid. Each app subclasses
 * this with its own logo and accent.
 *
 * This lives in :test-support's main source set, which is dexed at minSdk 26 for the apps'
 * androidTest APKs, so test names can't contain spaces (DEX 040+ only).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class LogoContract(@DrawableRes logoRes: Int, @ColorRes private val accentRes: Int) {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val logo = context.drawLogo(logoRes, SIZE)
    private val iconColor get() = logo.getPixel(SIZE / 2, EDGE)
    private val accent get() = context.getColor(accentRes)

    @Test
    fun logoIsTheAppIconARoundedSquare() {
        Color.alpha(logo.getPixel(0, 0)) shouldBe 0
        Color.alpha(logo.getPixel(SIZE / 2, SIZE / 2)) shouldBe OPAQUE
    }

    @Test
    fun iconsWhiteAnimalIsInTheMiddle() {
        logo.whiteShareOfMiddle() shouldBeGreaterThan MIN_ANIMAL
    }

    @Test
    fun launcherIconLayersAreTheAppsOwnNotCommonsStandIns() {
        for (layer in listOf("ic_launcher_background", "ic_launcher_foreground")) {
            @Suppress("DiscouragedApi") // :common's R can't name the app's own mipmaps
            val id = context.resources.getIdentifier(layer, "mipmap", context.packageName)
            withClue(layer) { context.getDrawable(id).shouldBeInstanceOf<BitmapDrawable>() }
        }
    }

    @Test
    fun accentHasTheIconsHue() {
        hueDistance(iconColor, accent) shouldBeLessThan MAX_HUE_DISTANCE
    }

    @Test
    fun accentIsALighterTintOfTheIconsColor() {
        withClue("accent lightness vs icon lightness") {
            lightness(accent) shouldBeGreaterThan lightness(iconColor)
        }
    }

    @Test
    fun accentIsVividNotWashedOut() {
        saturation(accent) shouldBeGreaterThanOrEqual MIN_ACCENT_SATURATION
    }

    private companion object {
        const val SIZE = 96
        const val EDGE = 4 // inside the rounded corners, clear of the animal
        const val MIN_ANIMAL = 0.15f
        const val MAX_HUE_DISTANCE = 20f
        const val MIN_ACCENT_SATURATION = 0.5f
        const val OPAQUE = 0xFF
    }
}
