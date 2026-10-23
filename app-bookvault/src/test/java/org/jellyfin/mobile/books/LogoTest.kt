package org.jellyfin.mobile.books

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.jellyfin.mobile.testsupport.drawLogo
import org.jellyfin.mobile.testsupport.hueDistance
import org.jellyfin.mobile.testsupport.whiteShareOfMiddle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The sign-in screen's logo is the app's icon, and the app's accent comes from that icon. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogoTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val logo = context.drawLogo(R.drawable.book_logo, SIZE)

    @Test
    fun `the logo is the app icon, a rounded square`() {
        logo.getPixel(0, 0) ushr 24 shouldBe 0
        logo.getPixel(SIZE / 2, SIZE / 2) ushr 24 shouldBe 0xFF
    }

    @Test
    fun `the icon's white animal is in the middle`() {
        logo.whiteShareOfMiddle() shouldBeGreaterThan MIN_ANIMAL
    }

    @Test
    fun `the app's accent is the icon's color`() {
        val iconColor = logo.getPixel(SIZE / 2, EDGE)
        hueDistance(iconColor, context.getColor(R.color.vault_accent)) shouldBeLessThan MAX_HUE_DISTANCE
    }

    private companion object {
        const val SIZE = 96
        const val EDGE = 4 // inside the rounded corners, clear of the animal
        const val MIN_ANIMAL = 0.15f
        const val MAX_HUE_DISTANCE = 20f
    }
}
